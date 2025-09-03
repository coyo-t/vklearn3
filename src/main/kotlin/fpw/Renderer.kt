package fpw

import fpw.ren.*
import fpw.ren.GPUtil.gpuCheck
import fpw.ren.GPUtil.imageBarrier
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT
import org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkShaderModuleCreateInfo.calloc
import java.awt.Color
import java.nio.ByteBuffer


class Renderer (val engineContext: Engine)
{
	val maxInFlightFrameCount = 2
	val preferredImageBufferingCount = 3
	var useVerticalSync = false
	var preferredPhysicalDevice: String? = null
	var useValidationLayers = true

	val clrValueColor = GPUtil.clearTintFrom(Color(0x7FB2E5))
	val clrValueDepth = VkClearValue.calloc().color {
		it.float32(0, 1f)
	}

	val instance = GPUInstance(
		this,
		VK_API_VERSION_1_3,
		validate = useValidationLayers,
	)

	val gpu = GPUDevice(
		this,
		preferredPhysicalDevice,
	)

	val memAlloc = MemAlloc(instance, gpu.hardwareDevice, gpu.logicalDevice)

	var displaySurface = DisplaySurface(instance, gpu.hardwareDevice, engineContext.window)
	var swapChain = SwapChain(
		this,
		gpu.logicalDevice,
		engineContext.window.wide,
		engineContext.window.tall,
		displaySurface,
		preferredImageBufferingCount,
		useVerticalSync,
	)

	var currentFrame = 0
		private set

	val graphicsQueue = CommandQueue.createGraphics(this, 0)
	val presentQueue = CommandQueue.createPresentation(this, 0)

	val swapChainDirectors = List(maxInFlightFrameCount) {
		SwapChainDirector(this)
	}

	private val meshManager = ModelsCache(this)
	private var doResize = false


	val descAllocator = DescriptorAllocator(gpu.hardwareDevice, gpu.logicalDevice)

	val mvMatrix = Matrix4f()

	val descriptorLayoutVertexStage = DescriptorLayout(
		this,
		DescriptorLayout.Info(
			DescriptorType.UNIFORM_BUFFER,
			0,
			1,
			VK_SHADER_STAGE_VERTEX_BIT
		),
	)

	var viewPoint: ViewPoint = IdentityViewPoint()


	val shaderMatrixBuffer = createHostVisibleBuffs(
		GPUtil.SIZEOF_MAT4 * 2L,
		maxInFlightFrameCount,
		VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
		"MATRIX",
		descriptorLayoutVertexStage,
	)

	val pipeline = run {
		val shPath = ResourceLocation.create("shader/scene.lua")
		val srcs = ShaderAssetThinger.loadFromLuaScript(shPath)
		val shaderModules = listOf(
			createShaderModule(
				VK_SHADER_STAGE_VERTEX_BIT,
				ShaderAssetThinger.compileSPIRV(
					srcs.vertex,
					ShaderAssetThinger.ShaderType.Vertex,
				),
			),
			createShaderModule(
				VK_SHADER_STAGE_FRAGMENT_BIT,
				ShaderAssetThinger.compileSPIRV(
					srcs.fragment,
					ShaderAssetThinger.ShaderType.Fragment,
				),
			),
		)
		val outs = Pipeline(
			this,
			shaderModules = shaderModules,
			vertexFormat = TestCube.format.vi,
			colorFormat = displaySurface.surfaceFormat.imageFormat,
			depthFormat = VK_FORMAT_D16_UNORM,
			descriptorSetLayouts = listOf(
				descriptorLayoutVertexStage,
			),
		)
		shaderModules.forEach { it.free() }
		outs
	}

	val textureManager = TextureManager(this)

	val textureTerrain = textureManager[ResourceLocation.create("image/terrain.png")]

	val currentSwapChainDirector get() = swapChainDirectors[currentFrame]

	fun init ()
	{
	}


	private fun submit(cmdBuff: CommandBuffer, currentFrame: Int, imageIndex: Int)
	{
		val director = swapChainDirectors[currentFrame]
		MemoryStack.stackPush().use { stack ->
			val fence = director.fence
			fence.reset()
			val commands = VkCommandBufferSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.commandBuffer(cmdBuff.vkCommandBuffer)
			val waits = VkSemaphoreSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
				.semaphore(director.imageAcquiredSemaphore.vkSemaphore)
			val signals = VkSemaphoreSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.stageMask(VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT)
				.semaphore(swapChain.renderThinger[imageIndex].renderCompleteFlag.vkSemaphore)
			graphicsQueue.submit(commands, waits, signals, fence)
		}
	}

	fun render ()
	{
		val director = swapChainDirectors[currentFrame]
		director.fence.waitForFences()
		val cmdPool = director.commandPool
		val cmdBuffer = director.commandBuffer

		cmdPool.reset()
		cmdBuffer.beginRecording()

		if (doResize)
		{
			resize()
			return
		}
		val imageIndex = swapChain.acquireNextImage(director.imageAcquiredSemaphore)
		if (imageIndex < 0)
		{
			resize()
			return
		}
		// scene render
		MemoryStack.stackPush().use { stack ->
			val thinger = swapChain.renderThinger[imageIndex]
			val swapChainVisualImage = thinger.imageView.vkImage
			val swapChainDepthImage = thinger.depthAttachment.image.vkImage
			val cmdHandle = cmdBuffer.vkCommandBuffer

			imageBarrier(
				stack,
				cmdHandle,
				swapChainVisualImage,
				VK_IMAGE_LAYOUT_UNDEFINED,
				VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
				VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
				VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
				VK_ACCESS_2_NONE,
				VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
				VK_IMAGE_ASPECT_COLOR_BIT,
			)
			imageBarrier(
				stack,
				cmdHandle,
				swapChainDepthImage,
				VK_IMAGE_LAYOUT_UNDEFINED,
				VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
				VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT or VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
				VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT or VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
				VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
				VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT or VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
				VK_IMAGE_ASPECT_DEPTH_BIT,
			)
			val renInf = thinger.renderInfo
			vkCmdBeginRendering(cmdHandle, renInf)
			vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.vkPipeline)
			val descr = stack.longs(
				descAllocator.getDescSet("MATRIX").vkDescriptorSet,
			)
			vkCmdBindDescriptorSets(
				cmdHandle,
				VK_PIPELINE_BIND_POINT_GRAPHICS,
				pipeline.vkPipelineLayout,
				0,
				descr,
				null,
			)
			val swapChainExtent = this.swapChain.extents
			val width = swapChainExtent.width()
			val height = swapChainExtent.height()
			val viewport = VkViewport.calloc(1, stack)
			viewport.x(0f)
			viewport.y(height.toFloat())
			viewport.height(-height.toFloat())
			viewport.width(width.toFloat())
			viewport.minDepth(0.0f)
			viewport.maxDepth(1.0f)
			vkCmdSetViewport(cmdHandle, 0, viewport)

			val scissor = VkRect2D.calloc(1, stack)
			scissor.extent { it.width(width).height(height) }
			scissor.offset { it.x(0).y(0) }
			vkCmdSetScissor(cmdHandle, 0, scissor)

//			shaderTextureUniform[currentFrame].setImages(device, textureSampler, 1, textureTerrain.imageView)


			val vbCount = 1
			val offsets = stack.mallocLong(vbCount).put(0, 0L)
			val vbAddress = stack.mallocLong(vbCount)

			viewPoint.updateMatricies()
			val viewMatrix = viewPoint.viewMatrix
			val projectionMatrix = viewPoint.projectionMatrix
			val entities = engineContext.entities
			val numEntities = entities.size
			val curMatrixBuffer = shaderMatrixBuffer[currentFrame]
			for (i in 0..<numEntities)
			{
				val entity = entities[i]
				val modelId = entity.model ?: continue
				val model = meshManager[modelId] ?: continue
				entity.updateModelMatrix()
				viewMatrix.mul(entity.modelMatrix, mvMatrix)

				GPUtil.copyMatrixToBuffer(curMatrixBuffer, projectionMatrix, 0)
				GPUtil.copyMatrixToBuffer(curMatrixBuffer, mvMatrix, GPUtil.SIZEOF_MAT4)

//				projectionMatrix.get(pushConstantsBuffer)
//				mvMatrix.get(GPUtil.SIZEOF_MAT4, pushConstantsBuffer)

//				vkCmdPushConstants(
//					cmdHandle, pipeline.vkPipelineLayout,
//					VK_SHADER_STAGE_VERTEX_BIT, 0,
//					pushConstantsBuffer
//				)
				vbAddress.put(0, model.verticesBuffer.bufferStruct)
				vkCmdBindVertexBuffers(
					cmdHandle,
					0,
					vbAddress,
					offsets,
				)
				vkCmdBindIndexBuffer(
					cmdHandle,
					model.indicesBuffer.bufferStruct,
					0,
					VK_INDEX_TYPE_UINT32
				)
				vkCmdDrawIndexed(cmdHandle, model.numIndices, 1, 0, 0, 0)
			}
			vkCmdEndRendering(cmdHandle)
			imageBarrier(
				stack,
				cmdHandle,
				swapChainVisualImage,
				VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
				VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
				VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
				VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
				VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
				VK_PIPELINE_STAGE_2_NONE,
				VK_IMAGE_ASPECT_COLOR_BIT,
			)
		}

		cmdBuffer.endRecording()

		submit(cmdBuffer, currentFrame, imageIndex)

		doResize = swapChain.presentImage(presentQueue, imageIndex)

		currentFrame = (currentFrame + 1) % maxInFlightFrameCount
	}

	private fun resize ()
	{
		val window = engineContext.window
		if (window.wide == 0 || window.tall == 0)
		{
			return
		}
		doResize = false
		gpu.logicalDevice.waitIdle()

		swapChain.free()
		displaySurface.free(instance)
		displaySurface = DisplaySurface(instance, gpu.hardwareDevice, window)
		swapChain = SwapChain(
			this,
			gpu.logicalDevice,
			window.wide,
			window.tall,
			displaySurface,
			preferredImageBufferingCount,
			useVerticalSync,
		)

		swapChainDirectors.forEach(SwapChainDirector::onResize)

		val extent = swapChain.extents
		engineContext.lens.resize(extent.width(), extent.height())

	}

	fun createShaderModule (shaderStage: Int, spirv: ByteBuffer): ShaderModule
	{
		val handle = MemoryStack.stackPush().use { stack ->
			val moduleCreateInfo = calloc(stack)
				.`sType$Default`()
				.pCode(spirv)

			val lp = stack.mallocLong(1)
			gpuCheck(
				vkCreateShaderModule(gpu.logicalDevice.vkDevice, moduleCreateInfo, null, lp),
				"Failed to create shader module"
			)
			lp.get(0)
		}
		return ShaderModule(
			this,
			handle = handle,
			shaderStage = shaderStage,
		)
	}

	fun createHostVisibleBuffs(
		buffSize: Long,
		numBuffs: Int,
		usage: Int,
		id: String,
		layout: DescriptorLayout,
	): List<GPUBuffer>
	{
		descAllocator.addDescSets(id, layout, numBuffs)
		val first = layout.layoutInfos.first()
		return List(numBuffs) {
			val r = GPUBuffer(
				this,
				buffSize,
				usage,
				VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
				VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
				VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
			)
			val descSet = descAllocator.getDescSet(id, it)
			descSet.setBuffer(
				gpu.logicalDevice,
				r,
				r.requestedSize,
				first.binding,
				first.descType.vk,
			)
			r
		}
	}
	fun createHostVisibleBuff (
		buffSize: Long,
		usage: Int,
		id: String,
		layout: DescriptorLayout,
	): GPUBuffer
	{
		val descSet = descAllocator.addDescSets(id, layout, 1).first()
		val buff = GPUBuffer(
			this,
			buffSize,
			usage,
			VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
			VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
			VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
		)
		val first = layout.layoutInfos.first()
		descSet.setBuffer(
			gpu.logicalDevice,
			buff,
			buff.requestedSize,
			first.binding,
			first.descType.vk
		)
		return buff
	}

	fun free()
	{
		gpu.logicalDevice.waitIdle()
		textureManager.free()

		descriptorLayoutVertexStage.free()
		shaderMatrixBuffer.forEach { it.free() }

		descAllocator.free()
		pipeline.free()
		clrValueColor.free()
		clrValueDepth.free()

		meshManager.free()
		swapChainDirectors.forEach { it.free() }

		swapChain.free()
		displaySurface.free(instance)
		memAlloc.free()
		gpu.free()
		instance.free()
	}
}