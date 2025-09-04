package fpw.ren

import fpw.Engine
import fpw.FUtil
import fpw.ren.command.CommandBuffer
import fpw.ren.command.CommandPool
import fpw.ren.command.CommandSequence
import fpw.ren.descriptor.*
import fpw.ren.device.GPUDevice
import fpw.ren.enums.ShaderType
import fpw.ren.enums.VkFormat
import fpw.ren.goobers.IdentityViewPoint
import fpw.ren.goobers.ViewPoint
import fpw.ren.image.ImageLayout
import fpw.ren.model.ModelManager
import fpw.ren.model.VertexFormatBuilder.Companion.buildVertexFormat
import fpw.ren.pipeline.Pipeline
import fpw.ren.texture.Sampler
import fpw.ren.texture.SamplerFilter
import fpw.ren.texture.SamplerWrapping
import fpw.ren.texture.TextureManager
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT
import org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VK13.*
import java.awt.Color
import kotlin.use

class Renderer (val engineContext: Engine)
{
	val preferredImageBufferingCount = 3
	var useVerticalSync = false
	var preferredPhysicalDevice: String? = null
	var useValidationLayers = true
	val testVertexFormat = buildVertexFormat {
		location3D()
		texcoord2D()
	}

	val clrValueColor = GPUtil.createClearValue(Color(0x7FB2E5))
	val clrValueDepth = GPUtil.createClearValue().color {
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

	val memAlloc = MemAlloc(instance, gpu)
//	val descAlloc = DescriptorAllocator(gpu)

	var displaySurface = DisplaySurface(instance, gpu, engineContext.window)
	val graphicsQueue = CommandSequence.createGraphics(this, 0)
	val presentQueue = CommandSequence.createPresentation(this, 0)

	var swapChain = SwapChain(
		this,
		gpu,
		engineContext.window.wide,
		engineContext.window.tall,
		displaySurface,
		preferredImageBufferingCount,
		useVerticalSync,
	)

	val SCDcommandPool
		= CommandPool(this, graphicsQueue.queueFamilyIndex, false)

	val SCDcommandBuffer
		= CommandBuffer(this, SCDcommandPool, oneTimeSubmit = true)

	var SCDimageAcquiredSemaphore
		= Semaphore(this)

	val SCDfence
		= Fence(this, signaled = true)

	private var doResize = false
	val meshManager = ModelManager(this)
	val shaderManager = ShaderCodeManager(this)
	var viewPoint: ViewPoint = IdentityViewPoint()
	val mvMatrix = Matrix4f()

	val textureManager = TextureManager(this)
	val textureSampler = Sampler(
		this,
		wrapping = SamplerWrapping.Repeat,
		filter = SamplerFilter.Nearest,
	)

	val textureTerrain = textureManager[engineContext.testTexture]


	val descriptors = DescAllocator(gpu).apply {
		init(
			1000,
			DescType.StorageImage to 3,
			DescType.StorageBuffer to 3,
			DescType.UniformBuffer to 3,
			DescType.CombinedImageSampler to 4,
		)
	}

	val uniformBufferDescLayout = DescSetLayout(
		this,
		DescSetLayout.Info(
			DescType.UniformBuffer,
			0,
			1,
			VK_SHADER_STAGE_VERTEX_BIT,
		),
		DescSetLayout.Info(
			DescType.CombinedImageSampler,
			1,
			1,
			VK_SHADER_STAGE_FRAGMENT_BIT,
		),
	)

	val shaderUniformBuffer = GPUBuffer(
		this,
		GPUtil.SIZEOF_MAT4 * 2L,
		VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
		VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
		VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
		VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
	)

	val FUCK_descsets = descriptors.allocate(uniformBufferDescLayout)

	val pipeline = run {
		val shCode = shaderManager[engineContext.testShader]
		val shaderModules = listOf(
			ShaderModule(
				this,
				shaderStage = ShaderType.Vertex,
				spirv = shCode.vertex,
			),
			ShaderModule(
				this,
				shaderStage = ShaderType.Fragment,
				spirv = shCode.fragment,
			),
		)
		val outs = Pipeline(
			this,
			shaderModules = shaderModules,
			vertexFormat = testVertexFormat,
			colorFormat = displaySurface.surfaceFormat.imageFormat,
			depthFormat = VkFormat.D16_UNORM,
			descriptorSetLayouts = listOf(
				uniformBufferDescLayout,
			),
		)
		shaderModules.forEach { it.free() }
		outs
	}

	fun init ()
	{
		MemoryStack.stackPush().use { s2 ->
			val writer = DescWriter(s2)
			writer.writeBuffer(
				0,
				shaderUniformBuffer,
				GPUtil.SIZEOF_MAT4 * 2L,
				0L,
				DescType.UniformBuffer,
			)
			writer.writeImage(
				1,
				textureTerrain.imageView,
				textureSampler,
				ImageLayout.OptimalShaderReadOnly,
				DescType.CombinedImageSampler,
			)
			writer.updateSet(gpu, FUCK_descsets)
		}
	}

	private fun submit(cmdBuff: CommandBuffer, imageIndex: Int)
	{
		MemoryStack.stackPush().use { stack ->
			val fence = SCDfence
			fence.reset()
			val commands = VkCommandBufferSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.commandBuffer(cmdBuff.vkCommandBuffer)
			val waits = VkSemaphoreSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
				.semaphore(SCDimageAcquiredSemaphore.vkSemaphore)
			val signals = VkSemaphoreSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.stageMask(VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT)
				.semaphore(swapChain.renderThinger[imageIndex].renderCompleteFlag.vkSemaphore)
			graphicsQueue.submit(commands, waits, signals, fence)
		}
	}

	fun render ()
	{
		SCDfence.waitForFences()
//		descriptors.clearPools()
		val cmdPool = SCDcommandPool
		val cmdBuffer = SCDcommandBuffer

		cmdPool.reset()
		cmdBuffer.beginRecording()

		if (doResize)
		{
			resize()
			return
		}
		val imageIndex = swapChain.acquireNextImage(SCDimageAcquiredSemaphore)
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

			GPUtil.imageBarrier(
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
			GPUtil.imageBarrier(
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
				FUCK_descsets.vkDescSet,
//				descAlloc.getDescSet("MATRIX").vkDescriptorSet,
//				descAlloc.getDescSet("TEXTURE").vkDescriptorSet,
			)
			vkCmdBindDescriptorSets(
				cmdHandle,
				VK_PIPELINE_BIND_POINT_GRAPHICS,
				pipeline.vkPipelineLayout,
				0,
				descr,
				null,
			)
			val writer = DescWriter(stack)
			writer.writeBuffer(
				0,
				shaderUniformBuffer,
				GPUtil.SIZEOF_MAT4 * 2L,
				0L,
				DescType.UniformBuffer,
			)
			writer.writeImage(
				1,
				textureTerrain.imageView,
				textureSampler,
				ImageLayout.OptimalShaderReadOnly,
				DescType.CombinedImageSampler,
			)
			writer.updateSet(gpu, FUCK_descsets)
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

//			shaderTextureShit[0].setImages(textureSampler, 1, textureTerrain.imageView)
			val offsets = stack.longs(0L)
			val vbAddress = stack.longs(0L)

			viewPoint.updateMatricies()
			val viewMatrix = viewPoint.viewMatrix
			val projectionMatrix = viewPoint.projectionMatrix
			val entities = engineContext.entities
			val numEntities = entities.size
			for (i in 0..<numEntities)
			{
				val entity = entities[i]
				val modelId = entity.model ?: continue
				val model = meshManager[modelId] ?: continue
				entity.updateModelMatrix()
				viewMatrix.mul(entity.modelMatrix, mvMatrix)

				GPUtil.copyMatrixToBuffer(shaderUniformBuffer, projectionMatrix, 0)
				GPUtil.copyMatrixToBuffer(shaderUniformBuffer, mvMatrix, GPUtil.SIZEOF_MAT4)

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
			GPUtil.imageBarrier(
				stack,
				cmdHandle,
				swapChainVisualImage,
				VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
				KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
				VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
				VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
				VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
				VK_PIPELINE_STAGE_2_NONE,
				VK_IMAGE_ASPECT_COLOR_BIT,
			)
		}

		cmdBuffer.endRecording()

		submit(cmdBuffer, imageIndex)

		doResize = swapChain.presentImage(presentQueue, imageIndex)
	}

	private fun resize ()
	{
		val window = engineContext.window
		if (window.wide == 0 || window.tall == 0)
		{
			return
		}
		gpu.logicalDevice.waitIdle()

		swapChain.free()
		displaySurface.free(instance)
		displaySurface = DisplaySurface(instance, gpu, window)
		swapChain = SwapChain(
			this,
			gpu,
			window.wide,
			window.tall,
			displaySurface,
			preferredImageBufferingCount,
			useVerticalSync,
		)

		SCDimageAcquiredSemaphore.free()
		SCDimageAcquiredSemaphore = Semaphore(this)

		val extent = swapChain.extents
		engineContext.lens.resize(extent.width(), extent.height())

		doResize = false
	}

//	fun createHostVisibleBuff (
//		buffSize: Long,
//		usage: Int,
//		id: String,
//		layout: DescriptorSetLayout,
//	): GPUBuffer
//	{
//		val descSet = descAlloc.addDescSets(id, layout, 1).first()
//		val buff = GPUBuffer(
//			this,
//			buffSize,
//			usage,
//			VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
//			VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
//			VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
//		)
//		val first = layout.layoutInfos.first()
//		descSet.setBuffer(
//			buff,
//			buff.requestedSize,
//			first.binding,
//			first.descType.vk
//		)
//		return buff
//	}

	fun free()
	{
		gpu.waitIdle()

		textureManager.free()
		shaderManager.free()

//		descriptorLayoutVertexStage.free()
//		descriptorLayoutFragmentStage.free()
//		shaderMatrixBuffer.free()
		textureSampler.free()

		descriptors.free()
//		descAlloc.free()
		pipeline.free()
		clrValueColor.free()
		clrValueDepth.free()

		shaderUniformBuffer.free()
		uniformBufferDescLayout.free()

		meshManager.free()
		SCDcommandBuffer.free(this, SCDcommandPool)
		SCDcommandPool.free()
		SCDimageAcquiredSemaphore.free()
		SCDfence.free()

		swapChain.free()
		displaySurface.free(instance)
		memAlloc.free()
		gpu.free()
		instance.free()
	}
}