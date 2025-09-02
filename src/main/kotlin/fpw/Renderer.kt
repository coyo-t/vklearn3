package fpw

import fpw.ren.ModelsCache
import fpw.ren.Texture
import fpw.ren.TextureManager
import fpw.ren.gpu.*
import fpw.ren.gpu.GPUtil.gpuCheck
import fpw.ren.gpu.GPUtil.imageBarrier
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkShaderModuleCreateInfo.calloc
import party.iroiro.luajava.value.LuaTableValue
import java.awt.Color
import java.nio.ByteBuffer


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

	var currentFrame = 0
		private set

	val graphicsQueue = CommandQueue.createGraphics(this, 0)
	val presentQueue = CommandQueue.createPresentation(this, 0)

	val swapChainDirectors = List(maxInFlightFrameCount) {
		SwapChainDirector(this)
	}

	var renderCompleteSemphs = List(swapChain.numImages) {
		createSemaphor()
	}
	private val meshManager = ModelsCache(this)
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

	val  descLayoutTexture = DescriptorLayout(
		this,
		DescriptorLayout.Info(
			DescriptorType.COMBINED_IMAGE_SAMPLER,
			0,
			1,
			VK_SHADER_STAGE_FRAGMENT_BIT
		)
	)


	val textureSampler = Sampler(
		this,
		filter = SamplerFilter.NEAREST,
		wrapping = SamplerWrapping.REPEAT,
	)

	val shaderMatrixBuffer = GPUtil.createHostVisibleBuff(
		this,
		GPUtil.SIZEOF_MAT4 * 2L,
		VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
		"MATRIX",
		descLayoutVtxUniform,
	)
	val shaderTextureUniform = descAllocator.addDescSets(
		"TEXTURE",
		descLayoutTexture,
	)

	val pipeline = run {
		val shaderModules = GPUtil.createShaderModules(this)
		val outs = GPUtil.createPipeline(
			this,
			shaderModules,
			listOf(
				descLayoutVtxUniform,
				descLayoutTexture,
			)
		)

		shaderModules.forEach { it.free(this) }
		outs
	}

	val textureManager = TextureManager(this)

	lateinit var textureTerrain: Texture

	val currentSwapChainDirector get() = swapChainDirectors[currentFrame]

	fun init (engine: Engine)
	{
		textureTerrain = textureManager[ResourceLocation.withDefaultNameSpace("image/terrain.png")]

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
				currentSwapChainDirector.commandPool,
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
		textureManager.free()

		descLayoutVtxUniform.free()
		descLayoutTexture.free()
		shaderMatrixBuffer.free()

		textureSampler.free()
		descAllocator.free()
		pipeline.cleanup()
		renderInfo.forEach { it.free() }
		attInfoColor.forEach { it.free() }
		attInfoDepth.forEach { it.free() }
		attDepth.forEach { it.free() }
		clrValueColor.free()
		clrValueDepth.free()

		meshManager.free()
		renderCompleteSemphs.forEach { it.free() }
//		imageAqSemphs.forEach { it.free(this) }
//		fences.forEach { it.free(this) }
//		for ((cb, cp) in cmdBuffers.zip(cmdPools))
//		{
//			cb.free(this, cp)
//			cp.free(this)
//		}
		swapChainDirectors.forEach(SwapChainDirector::free)

		pipelineCache.free(this)
		swapChain.cleanup(device)
		displaySurface.free(instance)
		device.free()
		hardware.free()
		instance.close()
	}

	private fun submit(cmdBuff: CommandBuffer, currentFrame: Int, imageIndex: Int)
	{
		val director = swapChainDirectors[currentFrame]
		MemoryStack.stackPush().use { stack ->
			val fence = director.fence
			fence.reset(this)
			val cmds = VkCommandBufferSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.commandBuffer(cmdBuff.vkCommandBuffer)
			val waitSemphs = VkSemaphoreSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
				.semaphore(director.imageAcquiredSemaphore.vkSemaphore)
			val signalSemphs = VkSemaphoreSubmitInfo.calloc(1, stack)
				.`sType$Default`()
				.stageMask(VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT)
				.semaphore(renderCompleteSemphs[imageIndex].vkSemaphore)
			graphicsQueue.submit(cmds, waitSemphs, signalSemphs, fence)
		}
	}

	fun render (engineContext: Engine)
	{
		val director = swapChainDirectors[currentFrame]
		director.fence.wait(this)
		val cmdPool = director.commandPool
		val cmdBuffer = director.commandBuffer

		cmdPool.reset(this)
		cmdBuffer.beginRecording()

		if (doResize)
		{
			resize(engineContext)
			return
		}
		val imageIndex = swapChain.acquireNextImage(device, director.imageAcquiredSemaphore)
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
			val swapChainExtent: VkExtent2D = swapChain.extents
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

			val descr = stack.mallocLong(2).apply {
				put(0, descAllocator.getDescSet("MATRIX").vkDescriptorSet)
				put(1, descAllocator.getDescSet("TEXTURE").vkDescriptorSet)
			}
			shaderTextureUniform.first().setImages(device, textureSampler, 1, textureTerrain.imageView)

			vkCmdBindDescriptorSets(
				cmdHandle,
				VK_PIPELINE_BIND_POINT_GRAPHICS,
				pipeline.vkPipelineLayout,
				0,
				descr,
				null,
			)

			val offsets = stack.mallocLong(1).put(0, 0L)
			val vertexBuffer = stack.mallocLong(1)

			val entities = engineContext.entities
			val numEntities = entities.size
			val projectionMatrix = engineContext.lens.projectionMatrix
			for (i in 0..<numEntities)
			{
				val entity = entities[i]
				if (entity === vp)
					continue
				val entityModelId = entity.modelId ?: continue
				val model = meshManager.modelMap[entityModelId] ?: continue
				entity.updateModelMatrix()
				viewpointMatrix.mul(entity.modelMatrix, mvMatrix)

				GPUtil.copyMatrixToBuffer(this, shaderMatrixBuffer, projectionMatrix, 0)
				GPUtil.copyMatrixToBuffer(this, shaderMatrixBuffer, mvMatrix, GPUtil.SIZEOF_MAT4)
//				projectionMatrix.get(pushConstantsBuffer)
//				mvMatrix.get(GPUtil.SIZEOF_MAT4, pushConstantsBuffer)

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

		swapChainDirectors.forEach(SwapChainDirector::onResize)

		renderCompleteSemphs.forEach { it.free() }
		renderCompleteSemphs = List(swapChain.numImages) {
			createSemaphor()
		}

		val extent = swapChain.extents
		engCtx.lens.resize(extent.width(), extent.height())

		renderInfo.forEach { it.free() }
		attInfoDepth.forEach { it.free() }
		attInfoColor.forEach { it.free() }
		attDepth.forEach { it.free() }
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
			val extent = this.swapChain.extents
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

	fun createCommandPool (
		queueFamilyIndex: Int,
		supportReset: Boolean,
	): CommandPool
	{
		MemoryStack.stackPush().use { stack ->
			val cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
				.`sType$Default`()
				.queueFamilyIndex(queueFamilyIndex)
			if (supportReset)
			{
				cmdPoolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
			}

			val lp = stack.mallocLong(1)
			gpuCheck(
				vkCreateCommandPool(device.vkDevice, cmdPoolInfo, null, lp),
				"Failed to create command pool"
			)
			return CommandPool(lp[0])
		}
	}

	fun createFence (signaled: Boolean): GPUFence
	{
		MemoryStack.stackPush().use { stack ->
			val fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
				.`sType$Default`()
				.flags(if (signaled) VK_FENCE_CREATE_SIGNALED_BIT else 0)
			val lp = stack.mallocLong(1)
			gpuCheck(vkCreateFence(vkDevice, fenceCreateInfo, null, lp), "Failed to create fence")
			return GPUFence(lp[0])
		}
	}

	fun createSemaphor(): Semaphore
	{
		MemoryStack.stackPush().use { stack ->
			val semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack).`sType$Default`()
			val lp = stack.mallocLong(1)
			gpuCheck(
				vkCreateSemaphore(vkDevice, semaphoreCreateInfo, null, lp),
				"Failed to create semaphore"
			)
			return Semaphore(this, lp[0])
		}
	}

	fun createShaderModule (shaderStage: Int, spirv: ByteBuffer): ShaderModule
	{
		return ShaderModule(
			handle = MemoryStack.stackPush().use { stack ->
				val moduleCreateInfo = calloc(stack)
					.`sType$Default`()
					.pCode(spirv)

				val lp = stack.mallocLong(1)
				gpuCheck(
					vkCreateShaderModule(vkDevice, moduleCreateInfo, null, lp),
					"Failed to create shader module"
				)
				lp.get(0)
			},
			shaderStage = shaderStage,
		)
	}
}