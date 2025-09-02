package fpw

import fpw.ren.ModelsCache
import fpw.ren.gpu.*
import fpw.ren.gpu.GPUtil.imageBarrier
import fpw.ren.gpu.CommandQueue
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR
import org.lwjgl.vulkan.VK14.*
import party.iroiro.luajava.value.LuaTableValue
import java.awt.Color


class Renderer (engineContext: Engine)
{
	val maxInFlightFrameCount = 2
	val preferredImageBufferingCount = 3
	var useVerticalSync = false
	var preferredPhysicalDevice: String? = null
	var useValidationLayers = true

	val instance = GPUInstance(
		validate = useValidationLayers,
	)
	val hardware = HardwareDevice.createPhysicalDevice(
		instance,
		prefDeviceName = preferredPhysicalDevice,
	)
	val device = LogicalDevice(hardware)
	var displaySurface = DisplaySurface(instance, hardware, engineContext.window)
	var swapChain = SwapChain(
		engineContext.window.wide,
		engineContext.window.tall,
		device,
		displaySurface,
		requestedImages = preferredImageBufferingCount,
		vsync = useVerticalSync,
	)

	val vkDevice get() = device.vkDevice

	val pipelineCache = device.createPipelineCache()

	private var currentFrame = 0

	private val graphicsQueue = CommandQueue.createGraphics(this, 0)
	private val presentQueue = CommandQueue.createPresentation(this, 0)

	private val cmdPools = List(maxInFlightFrameCount) {
		createCommandPool(graphicsQueue.queueFamilyIndex, false)
	}
	private val cmdBuffers = List(maxInFlightFrameCount) {
		CommandBuffer(this, cmdPools[it], oneTimeSubmit = true)
	}
	private var imageAqSemphs = List(maxInFlightFrameCount) {
		createSemaphor()
	}
	private var fences = List(maxInFlightFrameCount) {
		createFence(signaled = true)
	}
	private var renderCompleteSemphs = List(swapChain.numImages) {
		createSemaphor()
	}
	private val meshManager = ModelsCache()
	private var doResize = false

	val clrValueColor = GPUtil.clearTintFrom(Color(0x7FB2E5))
	val clrValueDepth = VkClearValue.calloc().color {
		it.float32(0, 1f)
	}
	var attDepth = GPUtil.createDepthAttachments(this)
	var attInfoColor = createColorAttachmentsInfo(clrValueColor)
	var attInfoDepth = GPUtil.createDepthAttachmentsInfo(this, attDepth, clrValueDepth)
	var renderInfo = createRenderInfo(attInfoColor, attInfoDepth)
	val pushConstantsBuffer = FUtil.createBuffer(128)

	val descAllocator = DescriptorAllocator(hardware, device)

	val viewpointMatrix = Matrix4f()
	val mvMatrix = Matrix4f()

	val descLayoutVtxUniform = DescriptorLayout(
		this,
		DescriptorLayout.Info(
			DescriptorType.UNIFORM_BUFFER,
			0,
			1,
			VK_SHADER_STAGE_VERTEX_BIT
		)
	)

	val buffProjMatrix = GPUtil.createHostVisibleBuff(
		this,
		GPUtil.SIZEOF_MAT4.toLong(),
		VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
		"MATRIX_PROJECTION",
		descLayoutVtxUniform,
	)

	val textureSampler = Sampler(
		this,
		filter = SamplerFilter.NEAREST,
		wrapping = SamplerWrapping.REPEAT,
	)

	val pipeline = run {
		val shaderModules = GPUtil.createShaderModules(this)
		GPUtil.createPipeline(this, shaderModules).also {
			shaderModules.forEach { it.free(this) }
		}
	}


	fun init (engine: Engine)
	{
		LuaCoyote().use { L ->
			L.openLibraries()
			val thing = (L.run(engine.testModel) as? LuaTableValue) ?: return@use
			val verticesTable = requireNotNull(thing["points"] as? LuaTableValue) {
				"model needs AT LEAST positions >:["
			}
			val vertexCount = verticesTable.length()
			val vertices = verticesTable.flatMap { (_, it) ->
				check(it is LuaTableValue)
				listOf(
					it[1].toNumber().toFloat(),
					it[2].toNumber().toFloat(),
					it[3].toNumber().toFloat(),
				)
			}.toFloatArray()

			val uvs = ((thing["uvs"] as? LuaTableValue)?.flatMap { (_, it) ->
				check(it is LuaTableValue)
				listOf(
					it[1].toNumber().toFloat(),
					it[2].toNumber().toFloat(),
				)
			}?.toFloatArray()) ?: FloatArray(vertexCount * 2) { 0f }

			val indices = requireNotNull(thing["indices"] as? LuaTableValue) {
				"I REQUIRE INDICES (for now -.-)"
			}.map { (_, it) ->
				it.toInteger().toInt()
			}.toIntArray()

			meshManager.loadModels(
				this,
				cmdPools[0],
				graphicsQueue,
				thing["temp_name"].toString(),
				Mesh(
					positions = vertices,
					texCoords = uvs,
					indices = indices
				),
			)
		}
	}

	fun free()
	{
		device.waitIdle()

		descLayoutVtxUniform.free(this)
		buffProjMatrix.free(this)

		textureSampler.free(this)
		descAllocator.free(device)
		pipeline.cleanup(this)
		renderInfo.forEach { it.free() }
		attInfoColor.forEach { it.free() }
		attInfoDepth.forEach { it.free() }
		attDepth.forEach { it.close(this) }
		clrValueColor.free()
		clrValueDepth.free()

		meshManager.close(this)
		renderCompleteSemphs.forEach { it.free(this) }
		imageAqSemphs.forEach { it.free(this) }
		fences.forEach { it.close(this) }
		for ((cb, cp) in cmdBuffers.zip(cmdPools))
		{
			cb.free(this, cp)
			cp.free(this)
		}
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
			graphicsQueue.submit(cmds, waitSemphs, signalSemphs, fence)
		}
	}

	fun render (engineContext: Engine)
	{
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
			val renInf = renderInfo[imageIndex]
			vkCmdBeginRendering(cmdHandle, renInf)
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
				val entityModelId = entity.modelId ?: continue
				val model = meshManager.modelMap[entityModelId] ?: continue
				engineContext.lens.projectionMatrix.get(pushConstantsBuffer)
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
			vkCmdEndRendering(cmdHandle)
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

		currentFrame = (currentFrame + 1) % maxInFlightFrameCount
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
			window.wide,
			window.tall,
			device,
			displaySurface,
			preferredImageBufferingCount,
			useVerticalSync,
		)

		renderCompleteSemphs.forEach { it.free(this) }
		imageAqSemphs.forEach { it.free(this) }

		imageAqSemphs = List(maxInFlightFrameCount) {
			createSemaphor()
		}

		renderCompleteSemphs = List(swapChain.numImages) {
			createSemaphor()
		}

		val extent = swapChain.swapChainExtent
		engCtx.lens.resize(extent.width(), extent.height())

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