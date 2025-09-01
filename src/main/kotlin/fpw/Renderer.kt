package fpw

import fpw.ren.ModelsCache
import fpw.ren.gpu.*
import fpw.ren.gpu.GPUtil.imageBarrier
import fpw.ren.gpu.queuez.GraphicsQueue
import fpw.ren.gpu.queuez.PresentQueue
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR
import org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers
import org.lwjgl.vulkan.VK14.*


class Renderer (engineContext: Engine)
{
	private val vkContext = GPUContext(engineContext.window)
	private var currentFrame = 0

	private val graphQueue = GraphicsQueue(vkContext, 0)
	private val presentQueue = PresentQueue(vkContext, 0)

	private val cmdPools = List(EngineConfig.maxInFlightFrames) {
		vkContext.createCommandPool(graphQueue.queueFamilyIndex, false)
	}
	private val cmdBuffers = cmdPools.map {
		CommandBuffer(vkContext, it, primary = true, oneTimeSubmit = true)
	}
	private var imageAqSemphs = List(EngineConfig.maxInFlightFrames) {
		vkContext.createSemaphor()
	}
	private var fences = List(EngineConfig.maxInFlightFrames) {
		vkContext.createFence(signaled = true)
	}
	private var renderCompleteSemphs = List(vkContext.swapChain.numImages) {
		vkContext.createSemaphor()
	}
	private val modelsCache = ModelsCache()
	private var doResize = false

	val clrValueColor = VkClearValue.calloc().color {
		it.float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1.0f)
	}
	val clrValueDepth = VkClearValue.calloc().color {
		it.float32(0, 1f)
	}
	var attDepth = GPUtil.createDepthAttachments(vkContext)
	var attInfoColor = createColorAttachmentsInfo(vkContext, clrValueColor)
	var attInfoDepth = GPUtil.createDepthAttachmentsInfo(vkContext, attDepth, clrValueDepth)
	var renderInfo = createRenderInfo(vkContext, attInfoColor, attInfoDepth)
	val pipeline = run {
		val shaderModules = GPUtil.createShaderModules(vkContext)
		GPUtil.createPipeline(vkContext, shaderModules).also {
			shaderModules.forEach { it.free(vkContext) }
		}
	}
	val pushConstantsBuffer = FUtil.createBuffer(128)



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
		val modelData = GPUModelData("Cubezor", listOf(meshData))

		modelsCache.loadModels(vkContext, listOf(modelData), cmdPools[0], graphQueue)
	}

	fun free()
	{
		vkContext.device.waitIdle()

		pipeline.cleanup(vkContext)
		renderInfo.forEach { it.free() }
		attInfoColor.forEach { it.free() }
		attInfoDepth.forEach { it.free() }
		attDepth.forEach { it.close(vkContext) }
		clrValueColor.free()
		clrValueDepth.free()

		modelsCache.close(vkContext)
		renderCompleteSemphs.forEach { it.free(vkContext) }
		imageAqSemphs.forEach { it.free(vkContext) }
		fences.forEach { it.close(vkContext) }
		for ((cb, cp) in cmdBuffers.zip(cmdPools))
		{
			cb.cleanup(vkContext, cp)
			cp.free(vkContext)
		}

		vkContext.free()
	}

	private fun submit(cmdBuff: CommandBuffer, currentFrame: Int, imageIndex: Int)
	{
		MemoryStack.stackPush().use { stack ->
			val fence = fences[currentFrame]
			fence.reset(vkContext)
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
		val swapChain = vkContext.swapChain

		fences[currentFrame].wait(vkContext)

		val cmdPool = cmdPools[currentFrame]
		val cmdBuffer = cmdBuffers[currentFrame]

		cmdPool.reset(vkContext)
		cmdBuffer.beginRecording()

		if (doResize)
		{
			resize(engineContext)
			return
		}
		val imageIndex = swapChain.acquireNextImage(vkContext.device, imageAqSemphs[currentFrame])
		if (imageIndex < 0)
		{
			resize(engineContext)
			return
		}
		// scene render
		MemoryStack.stackPush().use { stack ->
			val swapChain = vkContext.swapChain
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
					val model = modelsCache.modelMap[entity.modelId] ?: continue
					val vulkanMeshList = model.vulkanMeshList
					val numMeshes = vulkanMeshList.size
					engineContext.projection.projectionMatrix.get(pushConstantsBuffer)
					entity.modelMatrix.get(GPUtil.SIZEOF_MAT4, pushConstantsBuffer)
					vkCmdPushConstants(
						cmdHandle, pipeline.vkPipelineLayout,
						VK_SHADER_STAGE_VERTEX_BIT, 0,
						pushConstantsBuffer
					)
					for (j in 0..<numMeshes)
					{
						val vulkanMesh = vulkanMeshList[j]
						vertexBuffer.put(0, vulkanMesh.verticesBuffer.bufferStruct)
						vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets!!)
						vkCmdBindIndexBuffer(
							cmdHandle,
							vulkanMesh.indicesBuffer.bufferStruct,
							0,
							VK_INDEX_TYPE_UINT32
						)
						vkCmdDrawIndexed(cmdHandle, vulkanMesh.numIndices, 1, 0, 0, 0)
					}
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
		vkContext.device.waitIdle()

		vkContext.resize(window)

		renderCompleteSemphs.forEach { it.free(vkContext) }
		imageAqSemphs.forEach { it.free(vkContext) }

		imageAqSemphs = List(EngineConfig.maxInFlightFrames) {
			vkContext.createSemaphor()
		}

		renderCompleteSemphs = List(vkContext.swapChain.numImages) {
			vkContext.createSemaphor()
		}

		val extent = vkContext.swapChain.swapChainExtent
		engCtx.projection.resize(extent.width(), extent.height())

		renderInfo.forEach { it.free() }
		attInfoDepth.forEach { it.free() }
		attInfoColor.forEach { it.free() }
		attDepth.forEach { it.close(vkContext) }
		attDepth = GPUtil.createDepthAttachments(vkContext)
		attInfoColor = createColorAttachmentsInfo(vkContext, clrValueColor)
		attInfoDepth = GPUtil.createDepthAttachmentsInfo(vkContext, attDepth, clrValueDepth)
		renderInfo = createRenderInfo(vkContext, attInfoColor, attInfoDepth)
	}

	fun createColorAttachmentsInfo(
		vkCtx: GPUContext,
		clearValue: VkClearValue
	): List<VkRenderingAttachmentInfo.Buffer>
	{
		val swapChain = vkCtx.swapChain
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
		vkCtx: GPUContext,
		colorAttachments: List<VkRenderingAttachmentInfo.Buffer>,
		depthAttachments: List<VkRenderingAttachmentInfo>,
	): List<VkRenderingInfo>
	{
		val swapChain = vkCtx.swapChain
		val numImages = swapChain.numImages

		MemoryStack.stackPush().use { stack ->
			val extent = swapChain.swapChainExtent
			val renderArea = VkRect2D.calloc(stack).extent(extent)
			return List(numImages) {
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