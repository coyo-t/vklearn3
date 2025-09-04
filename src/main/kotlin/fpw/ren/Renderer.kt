package fpw.ren

import fpw.Engine
import fpw.ren.command.CommandBuffer
import fpw.ren.command.CommandSequence
import fpw.ren.descriptor.*
import fpw.ren.descriptor.DescriptorAllocatorGrowable.PoolSizeRatio
import fpw.ren.device.GPUDevice
import fpw.ren.enums.ShaderType
import fpw.ren.goobers.IdentityViewPoint
import fpw.ren.goobers.ViewPoint
import fpw.ren.model.ModelManager
import fpw.ren.model.VertexFormatBuilder.Companion.buildVertexFormat
import fpw.ren.pipeline.Pipeline
import fpw.ren.texture.Sampler
import fpw.ren.texture.SamplerFilter
import fpw.ren.texture.SamplerWrapping
import fpw.ren.texture.TextureManager
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.vma.Vma
import org.lwjgl.vulkan.*
import java.awt.Color

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
		VK13.VK_API_VERSION_1_3,
		validate = useValidationLayers,
	)

	val gpu = GPUDevice(
		this,
		preferredPhysicalDevice,
	)

	val memAlloc = MemAlloc(instance, gpu)
	val descAlloc = DescriptorAllocator(gpu)

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


	val swapChainDirector = SwapChainDirector(this)

	private var doResize = false
	val meshManager = ModelManager(this)
	val shaderManager = ShaderCodeManager(this)
	var viewPoint: ViewPoint = IdentityViewPoint()
	val mvMatrix = Matrix4f()


	val descriptorLayoutVertexStage = DescriptorSetLayout(
		this,
		DescriptorSetLayout.Info(
			DescriptorType.UniformBuffer,
			0,
			1,
			VK10.VK_SHADER_STAGE_VERTEX_BIT
		),
	)

	val descriptorLayoutFragmentStage = DescriptorSetLayout(
		this,
		DescriptorSetLayout.Info(
			DescriptorType.CombinedImageSampler,
			1,
			1,
			VK10.VK_SHADER_STAGE_FRAGMENT_BIT
		),
	)


	val shaderMatrixBuffer = createHostVisibleBuff(
		GPUtil.SIZEOF_MAT4 * 2L,
		VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
		"MATRIX",
		descriptorLayoutVertexStage,
	)
	val shaderTextureShit = descAlloc.addDescSets(
		"TEXTURE",
		descriptorLayoutFragmentStage,
		1,
	)

	init
	{
		val writer = DescWriter()
//		writer.writeBuffer()
	}

	val descriptors = DescriptorAllocatorGrowable(gpu)

	init
	{
		descriptors.init(
			1000,
			DescriptorType.StorageImage to 3,
			DescriptorType.StorageBuffer to 3,
			DescriptorType.UniformBuffer to 3,
			DescriptorType.CombinedImageSampler to 4,
		)
	}

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
			depthFormat = VK10.VK_FORMAT_D16_UNORM,
			descriptorSetLayouts = listOf(
				descriptorLayoutVertexStage,
				descriptorLayoutFragmentStage,
			),
		)
		shaderModules.forEach { it.free() }
		outs
	}

	val textureManager = TextureManager(this)
	val textureSampler = Sampler(
		this,
		wrapping = SamplerWrapping.Repeat,
		filter = SamplerFilter.Nearest,
	)

	val textureTerrain = textureManager[engineContext.testTexture]

	fun init ()
	{
		buildVertexFormat {
			location3D()
			texcoord2D()
			normal()
			tint8()
		}

	}


	private fun submit(cmdBuff: CommandBuffer, imageIndex: Int)
	{
		MemoryStack.stackPush().use { stack ->
			val fence = swapChainDirector.fence
			fence.reset()
			val commands = VkCommandBufferSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.commandBuffer(cmdBuff.vkCommandBuffer)
			val waits = VkSemaphoreSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.stageMask(VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
				.semaphore(swapChainDirector.imageAcquiredSemaphore.vkSemaphore)
			val signals = VkSemaphoreSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.stageMask(VK13.VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT)
				.semaphore(swapChain.renderThinger[imageIndex].renderCompleteFlag.vkSemaphore)
			graphicsQueue.submit(commands, waits, signals, fence)
		}
	}

	fun render ()
	{
		val director = swapChainDirector
		director.fence.waitForFences()
		descriptors.clearPools()
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

			GPUtil.imageBarrier(
				stack,
				cmdHandle,
				swapChainVisualImage,
				VK10.VK_IMAGE_LAYOUT_UNDEFINED,
				VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
				VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
				VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
				VK13.VK_ACCESS_2_NONE,
				VK13.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
				VK10.VK_IMAGE_ASPECT_COLOR_BIT,
			)
			GPUtil.imageBarrier(
				stack,
				cmdHandle,
				swapChainDepthImage,
				VK10.VK_IMAGE_LAYOUT_UNDEFINED,
				VK12.VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
				VK13.VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT or VK13.VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
				VK13.VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT or VK13.VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
				VK13.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
				VK13.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT or VK13.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
				VK10.VK_IMAGE_ASPECT_DEPTH_BIT,
			)
			val renInf = thinger.renderInfo
			VK13.vkCmdBeginRendering(cmdHandle, renInf)
			VK10.vkCmdBindPipeline(cmdHandle, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.vkPipeline)
			val descr = stack.longs(
				descAlloc.getDescSet("MATRIX").vkDescriptorSet,
				descAlloc.getDescSet("TEXTURE").vkDescriptorSet,
			)
			VK10.vkCmdBindDescriptorSets(
				cmdHandle,
				VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
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
			VK10.vkCmdSetViewport(cmdHandle, 0, viewport)

			val scissor = VkRect2D.calloc(1, stack)
			scissor.extent { it.width(width).height(height) }
			scissor.offset { it.x(0).y(0) }
			VK10.vkCmdSetScissor(cmdHandle, 0, scissor)


			shaderTextureShit[0].setImages(textureSampler, 1, textureTerrain.imageView)


			val vbCount = 1
			val offsets = stack.mallocLong(vbCount).put(0, 0L)
			val vbAddress = stack.mallocLong(vbCount)

			viewPoint.updateMatricies()
			val viewMatrix = viewPoint.viewMatrix
			val projectionMatrix = viewPoint.projectionMatrix
			val entities = engineContext.entities
			val numEntities = entities.size
			val curMatrixBuffer = shaderMatrixBuffer
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
				VK10.vkCmdBindVertexBuffers(
					cmdHandle,
					0,
					vbAddress,
					offsets,
				)
				VK10.vkCmdBindIndexBuffer(
					cmdHandle,
					model.indicesBuffer.bufferStruct,
					0,
					VK10.VK_INDEX_TYPE_UINT32
				)
				VK10.vkCmdDrawIndexed(cmdHandle, model.numIndices, 1, 0, 0, 0)
			}
			VK13.vkCmdEndRendering(cmdHandle)
			GPUtil.imageBarrier(
				stack,
				cmdHandle,
				swapChainVisualImage,
				VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
				KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
				VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
				VK13.VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
				VK13.VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT or VK13.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
				VK13.VK_PIPELINE_STAGE_2_NONE,
				VK10.VK_IMAGE_ASPECT_COLOR_BIT,
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

		swapChainDirector.onResize()

		val extent = swapChain.extents
		engineContext.lens.resize(extent.width(), extent.height())

		doResize = false
	}

	fun createHostVisibleBuff (
		buffSize: Long,
		usage: Int,
		id: String,
		layout: DescriptorSetLayout,
	): GPUBuffer
	{
		val descSet = descAlloc.addDescSets(id, layout, 1).first()
		val buff = GPUBuffer(
			this,
			buffSize,
			usage,
			Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
			Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
			VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
		)
		val first = layout.layoutInfos.first()
		descSet.setBuffer(
			buff,
			buff.requestedSize,
			first.binding,
			first.descType.vk
		)
		return buff
	}

	fun free()
	{
		gpu.waitIdle()

		textureManager.free()
		shaderManager.free()

		descriptorLayoutVertexStage.free()
		descriptorLayoutFragmentStage.free()
		shaderMatrixBuffer.free()
		textureSampler.free()

		descriptors.free()
		descAlloc.free()
		pipeline.free()
		clrValueColor.free()
		clrValueDepth.free()

		meshManager.free()
		swapChainDirector.free()

		swapChain.free()
		displaySurface.free(instance)
		memAlloc.free()
		gpu.free()
		instance.free()
	}
}