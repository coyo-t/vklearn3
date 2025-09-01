package fpw

import fpw.ren.ModelsCache
import fpw.ren.gpu.*
import fpw.ren.gpu.GPUtil.imageBarrier
import fpw.ren.gpu.queuez.GraphicsQueue
import fpw.ren.gpu.queuez.PresentQueue
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR
import org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers
import org.lwjgl.vulkan.VK14.*


class Renderer (engineContext: Engine)
{
	val instance = GPUInstance(
		validate = EngineConfig.useVulkanValidationLayers,
	)
	val hardware = HardwareDevice.createPhysicalDevice(
		instance,
		prefDeviceName = EngineConfig.preferredPhysicalDevice,
	)
	val device = LogicalDevice(hardware)
	var displaySurface = DisplaySurface(instance, hardware, engineContext.window)
	var swapChain = SwapChain(
		engineContext.window,
		device,
		displaySurface,
		requestedImages = EngineConfig.preferredImageBufferingCount,
		vsync = EngineConfig.useVerticalSync,
	)

	val vkDevice get() = device.vkDevice

	val pipelineCache = device.createPipelineCache()


	private var currentFrame = 0

	private val graphQueue = GraphicsQueue(this, 0)
	private val presentQueue = PresentQueue(this, 0)

	private val cmdPools = List(EngineConfig.maxInFlightFrames) {
		createCommandPool(graphQueue.queueFamilyIndex, false)
	}
	private val cmdBuffers = cmdPools.map {
		CommandBuffer(this, it, primary = true, oneTimeSubmit = true)
	}
	private var imageAqSemphs = List(EngineConfig.maxInFlightFrames) {
		createSemaphor()
	}
	private var fences = List(EngineConfig.maxInFlightFrames) {
		createFence(signaled = true)
	}
	private var renderCompleteSemphs = List(swapChain.numImages) {
		createSemaphor()
	}
	private val modelsCache = ModelsCache()
	private var doResize = false

	val clrValueColor = VkClearValue.calloc().color {
		it.float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1.0f)
	}
	val clrValueDepth = VkClearValue.calloc().color {
		it.float32(0, 1f)
	}
	var attDepth = GPUtil.createDepthAttachments(this)
	var attInfoColor = createColorAttachmentsInfo(clrValueColor)
	var attInfoDepth = GPUtil.createDepthAttachmentsInfo(this, attDepth, clrValueDepth)
	var renderInfo = createRenderInfo(attInfoColor, attInfoDepth)
	val pipeline = run {
		val shaderModules = GPUtil.createShaderModules(this)
		GPUtil.createPipeline(this, shaderModules).also {
			shaderModules.forEach { it.free(this) }
		}
	}
	val pushConstantsBuffer = FUtil.createBuffer(128)

	val descAllocator = DescriptorAllocator(hardware, device)

	val viewpointMatrix = Matrix4f()
	val mvMatrix = Matrix4f()

	fun init ()
	{
		val meshData = GPUMeshData(
			positions = floatArrayOf(
				-0.5f, +0.5f, +0.5f,
				-0.5f, -0.5f, +0.5f,
				+0.5f, -0.5f, +0.5f,
				+0.5f, +0.5f, +0.5f,
				-0.5f, +0.5f, -0.5f,
				+0.5f, +0.5f, -0.5f,
				-0.5f, -0.5f, -0.5f,
				+0.5f, -0.5f, -0.5f,
			),
			texCoords = floatArrayOf(
				0.0f, 0.0f,
				0.5f, 0.0f,
				1.0f, 0.0f,
				1.0f, 0.5f,
				1.0f, 1.0f,
				0.5f, 1.0f,
				0.0f, 1.0f,
				0.0f, 0.5f,
			),
			indices = intArrayOf(
				// Front face
				0, 1, 3, 3, 1, 2,
				// Top Face
				4, 0, 3, 5, 4, 3,
				// Right face
				3, 2, 7, 5, 3, 7,
				// Left face
				6, 1, 0, 6, 0, 4,
				// Bottom face
				2, 1, 6, 2, 6, 7,
				// Back face
				7, 6, 4, 7, 4, 5,
			)
		)

		modelsCache.loadModels(
			this,
			cmdPools[0],
			graphQueue,
			"Cubezor" to listOf(meshData),
		)
	}

	fun free()
	{
		device.waitIdle()

		pipeline.cleanup(this)
		renderInfo.forEach { it.free() }
		attInfoColor.forEach { it.free() }
		attInfoDepth.forEach { it.free() }
		attDepth.forEach { it.close(this) }
		clrValueColor.free()
		clrValueDepth.free()

		modelsCache.close(this)
		renderCompleteSemphs.forEach { it.free(this) }
		imageAqSemphs.forEach { it.free(this) }
		fences.forEach { it.close(this) }
		for ((cb, cp) in cmdBuffers.zip(cmdPools))
		{
			cb.cleanup(this, cp)
			cp.free(this)
		}
		descAllocator.free(device)
		pipelineCache.free(this)
		swapChain.cleanup(device)
		displaySurface.free(instance)
		device.close()
		hardware.free()
		instance.close()
	}

	private fun submit(cmdBuff: CommandBuffer, currentFrame: Int, imageIndex: Int)
	{
		MemoryStack.stackPush().use { stack ->
			val fence = fences[currentFrame]
			fence.reset(this)
			val cmds = VkCommandBufferSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.commandBuffer(cmdBuff.vkCommandBuffer)
			val waitSemphs = VkSemaphoreSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
				.semaphore(imageAqSemphs[currentFrame].vkSemaphore)
			val signalSemphs = VkSemaphoreSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.stageMask(VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT)
				.semaphore(renderCompleteSemphs[imageIndex].vkSemaphore)
			graphQueue.submit(cmds, waitSemphs, signalSemphs, fence)
		}
	}

	fun render (engineContext: Engine)
	{
		val swapChain = swapChain

		fences[currentFrame].wait(this)

		val cmdPool = cmdPools[currentFrame]
		val cmdBuffer = cmdBuffers[currentFrame]

		cmdPool.reset(this)
		cmdBuffer.beginRecording()

		if (doResize)
		{
			resize(engineContext)
			return
		}
		val imageIndex = swapChain.acquireNextImage(device, imageAqSemphs[currentFrame])
		if (imageIndex < 0)
		{
			resize(engineContext)
			return
		}
		// scene render
		MemoryStack.stackPush().use { stack ->
			val swapChain = swapChain
			val swapChainImage = swapChain.imageViews[imageIndex].vkImage
			val cmdHandle = cmdBuffer.vkCommandBuffer

			imageBarrier(
				stack,
				cmdHandle,
				swapChainImage,
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
				attDepth[imageIndex].image.vkImage,
				VK_IMAGE_LAYOUT_UNDEFINED,
				VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
				(
					VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT or
					VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT
				),
				(
					VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT or
					VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT
				),
				VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
				(
					VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT or
					VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
				),
				VK_IMAGE_ASPECT_DEPTH_BIT,
			)

			GPUtil.renderScoped(cmdHandle, renderInfo[imageIndex]) {
				val vp = engineContext.viewPoint?.apply {
					updateModelMatrix()
					viewpointMatrix.set(modelMatrix).invert()
				}
				vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.vkPipeline)
				val swapChainExtent: VkExtent2D = swapChain.swapChainExtent
				val width = swapChainExtent.width()
				val height = swapChainExtent.height()
				val viewport = VkViewport.calloc(1, stack)
					.x(0f)
					.y(height.toFloat())
					.height(-height.toFloat())
					.width(width.toFloat())
					.minDepth(0.0f)
					.maxDepth(1.0f)
				vkCmdSetViewport(cmdHandle, 0, viewport)

				val scissor = VkRect2D.calloc(1, stack)
					.extent { it.width(width).height(height) }
					.offset { it.x(0).y(0) }
				vkCmdSetScissor(cmdHandle, 0, scissor)

				val offsets = stack.mallocLong(1).put(0, 0L)
				val vertexBuffer = stack.mallocLong(1)

				val entities = engineContext.entities
				val numEntities = entities.size
				for (i in 0..<numEntities)
				{
					val entity = entities[i]
					if (entity === vp)
						continue
					val entityModelId = entity.modelId
					if (entityModelId.isEmpty())
						continue
					val model = modelsCache.modelMap[entityModelId] ?: continue
					engineContext.projection.projectionMatrix.get(pushConstantsBuffer)
					entity.updateModelMatrix()
					viewpointMatrix.mul(entity.modelMatrix, mvMatrix)
					mvMatrix.get(GPUtil.SIZEOF_MAT4, pushConstantsBuffer)
					vkCmdPushConstants(
						cmdHandle, pipeline.vkPipelineLayout,
						VK_SHADER_STAGE_VERTEX_BIT, 0,
						pushConstantsBuffer
					)
					vertexBuffer.put(0, model.verticesBuffer.bufferStruct)
					vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets!!)
					vkCmdBindIndexBuffer(
						cmdHandle,
						model.indicesBuffer.bufferStruct,
						0,
						VK_INDEX_TYPE_UINT32
					)
					vkCmdDrawIndexed(cmdHandle, model.numIndices, 1, 0, 0, 0)
				}
			}
			imageBarrier(
				stack,
				cmdHandle,
				swapChainImage,
				VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
				VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
				VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
				VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
				(
					VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT or
					VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT
				),
				VK_PIPELINE_STAGE_2_NONE,
				VK_IMAGE_ASPECT_COLOR_BIT,
			)
		}

		cmdBuffer.endRecording()

		submit(cmdBuffer, currentFrame, imageIndex)

		doResize = swapChain.presentImage(presentQueue, renderCompleteSemphs[imageIndex], imageIndex)

		currentFrame = (currentFrame + 1) % EngineConfig.maxInFlightFrames
	}

	private fun resize (engCtx: Engine)
	{
		val window = engCtx.window
		if (window.wide == 0 || window.tall == 0)
		{
			return
		}
		doResize = false
		device.waitIdle()

		swapChain.cleanup(device)
		displaySurface.free(instance)
		displaySurface = DisplaySurface(instance, hardware, window)
		swapChain = SwapChain(
			window,
			device,
			displaySurface,
			EngineConfig.preferredImageBufferingCount,
			EngineConfig.useVerticalSync,
		)

		renderCompleteSemphs.forEach { it.free(this) }
		imageAqSemphs.forEach { it.free(this) }

		imageAqSemphs = List(EngineConfig.maxInFlightFrames) {
			createSemaphor()
		}

		renderCompleteSemphs = List(swapChain.numImages) {
			createSemaphor()
		}

		val extent = swapChain.swapChainExtent
		engCtx.projection.resize(extent.width(), extent.height())

		renderInfo.forEach { it.free() }
		attInfoDepth.forEach { it.free() }
		attInfoColor.forEach { it.free() }
		attDepth.forEach { it.close(this) }
		attDepth = GPUtil.createDepthAttachments(this)
		attInfoColor = createColorAttachmentsInfo(clrValueColor)
		attInfoDepth = GPUtil.createDepthAttachmentsInfo(this, attDepth, clrValueDepth)
		renderInfo = createRenderInfo(attInfoColor, attInfoDepth)
	}

	fun createColorAttachmentsInfo(
		clearValue: VkClearValue
	): List<VkRenderingAttachmentInfo.Buffer>
	{
		val swapChain = swapChain
		return List(swapChain.numImages) {
			VkRenderingAttachmentInfo.calloc(1)
				.`sType$Default`()
				.imageView(swapChain.imageViews[it].vkImageView)
				.imageLayout(VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR)
				.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
				.storeOp(VK_ATTACHMENT_STORE_OP_STORE)
				.clearValue(clearValue)
		}
	}

	fun createRenderInfo(
		colorAttachments: List<VkRenderingAttachmentInfo.Buffer>,
		depthAttachments: List<VkRenderingAttachmentInfo>,
	): List<VkRenderingInfo>
	{

		MemoryStack.stackPush().use { stack ->
			val extent = this.swapChain.swapChainExtent
			val renderArea = VkRect2D.calloc(stack).extent(extent)
			return List(swapChain.numImages) {
				VkRenderingInfo.calloc()
					.`sType$Default`()
					.renderArea(renderArea)
					.layerCount(1)
					.pColorAttachments(colorAttachments[it])
					.pDepthAttachment(depthAttachments[it])
			}
		}
	}

}