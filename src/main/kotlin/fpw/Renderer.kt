package fpw

import fpw.ren.ModelsCache
import fpw.ren.ShaderAssetThinger
import fpw.ren.Texture
import fpw.ren.TextureManager
import fpw.ren.gpu.*
import fpw.ren.gpu.GPUtil.freeAll
import fpw.ren.gpu.GPUtil.gpuCheck
import fpw.ren.gpu.GPUtil.imageBarrier
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT
import org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkShaderModuleCreateInfo.calloc
import party.iroiro.luajava.value.LuaTableValue
import java.awt.Color
import java.nio.ByteBuffer
import kotlin.io.path.Path


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

	val memAlloc = MemAlloc(instance, hardware, device)

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
	var attDepth = createDepthAttachments()
	var attInfoColor = createColorAttachmentsInfo(clrValueColor)
	var attInfoDepth = createDepthAttachmentsInfo(attDepth, clrValueDepth)
	var renderInfo = createRenderInfo(attInfoColor, attInfoDepth)

	val descAllocator = DescriptorAllocator(hardware, device)

	val mvMatrix = Matrix4f()

	val descLayoutVtxUniform = DescriptorLayout(
		this,
		DescriptorLayout.Info(
			DescriptorType.UNIFORM_BUFFER,
			0,
			1,
			VK_SHADER_STAGE_VERTEX_BIT
		),
		DescriptorLayout.Info(
			DescriptorType.COMBINED_IMAGE_SAMPLER,
			0,
			1,
			VK_SHADER_STAGE_FRAGMENT_BIT
		)
	)

	val descLayoutTexture = DescriptorLayout(
		this,
		DescriptorLayout.Info(
			DescriptorType.COMBINED_IMAGE_SAMPLER,
			0,
			1,
			VK_SHADER_STAGE_FRAGMENT_BIT
		)
	)

	var viewPoint: ViewPoint = IdentityViewPoint()

	val textureSampler = Sampler(
		this,
		filter = SamplerFilter.NEAREST,
		wrapping = SamplerWrapping.REPEAT,
	)

	val shaderMatrixBuffer = createHostVisibleBuffs(
		GPUtil.SIZEOF_MAT4 * 2L,
		maxInFlightFrameCount,
		VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
		"MATRIX",
		descLayoutVtxUniform,
	)
	val shaderTextureUniform = descAllocator.addDescSets(
		"TEXTURE",
		descLayoutVtxUniform,
		maxInFlightFrameCount,
//		descLayoutTexture,
	)

	val pipeline = run {
		val shaderModules = createShaderModules()
		val outs = createPipeline(
			shaderModules,
			listOf(
				descLayoutVtxUniform,
				descLayoutTexture,
			)
		)

		shaderModules.forEach { it.free() }
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

	private fun submit(cmdBuff: CommandBuffer, currentFrame: Int, imageIndex: Int)
	{
		val director = swapChainDirectors[currentFrame]
		MemoryStack.stackPush().use { stack ->
			val fence = director.fence
			fence.reset(this)
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
				.semaphore(renderCompleteSemphs[imageIndex].vkSemaphore)
			graphicsQueue.submit(commands, waits, signals, fence)
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
				VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT or VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
				VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT or VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
				VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
				VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT or VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
				VK_IMAGE_ASPECT_DEPTH_BIT,
			)
			val renInf = renderInfo[imageIndex]
			vkCmdBeginRendering(cmdHandle, renInf)
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
			shaderTextureUniform[currentFrame].setImages(device, textureSampler, 1, textureTerrain.imageView)

			vkCmdBindDescriptorSets(
				cmdHandle,
				VK_PIPELINE_BIND_POINT_GRAPHICS,
				pipeline.vkPipelineLayout,
				0,
				descr,
				null,
			)

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
				val modelId = entity.modelId ?: continue
				val model = meshManager.modelMap[modelId] ?: continue
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
		attDepth = createDepthAttachments()
		attInfoColor = createColorAttachmentsInfo(clrValueColor)
		attInfoDepth = createDepthAttachmentsInfo(attDepth, clrValueDepth)
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
			gpuCheck(
				vkCreateFence(vkDevice, fenceCreateInfo, null, lp),
				"Failed to create fence",
			)
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
		val handle = MemoryStack.stackPush().use { stack ->
			val moduleCreateInfo = calloc(stack)
				.`sType$Default`()
				.pCode(spirv)

			val lp = stack.mallocLong(1)
			gpuCheck(
				vkCreateShaderModule(vkDevice, moduleCreateInfo, null, lp),
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

	fun createDepthAttachmentsInfo(
		depthAttachments: List<Attachment>,
		clearValue: VkClearValue
	): List<VkRenderingAttachmentInfo>
	{
		val swapChain = swapChain
		val numImages = swapChain.numImages
		return List(numImages) {
			VkRenderingAttachmentInfo.calloc()
				.`sType$Default`()
				.imageView(depthAttachments[it].imageView.vkImageView)
				.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
				.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
				.storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
				.clearValue(clearValue)
		}
	}

	fun createPipeline (
		shaderModules: List<ShaderModule>,
		descLayouts: List<DescriptorLayout>,
	): Pipeline
	{
		val buildInfo = Pipeline.Info(
			shaderModules = shaderModules,
			vi = TestCube.format.vi,
			colorFormat = displaySurface.surfaceFormat.imageFormat,
			depthFormat = VK_FORMAT_D16_UNORM,
			pushConstRange = listOf(
//				Triple(VK_SHADER_STAGE_VERTEX_BIT, 0, 128),
			),
			descriptorSetLayouts = descLayouts,
		)
		return Pipeline(this, buildInfo)
	}

	fun createDepthAttachments (): List<Attachment>
	{
		val numImages = swapChain.numImages
		val swapChainExtent = swapChain.extents
		return List(numImages) {
			Attachment(
				this,
				swapChainExtent.width(),
				swapChainExtent.height(),
				VK_FORMAT_D16_UNORM,
				VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
			)
		}
	}

	fun createShaderModules(): List<ShaderModule>
	{
		val srcs = ShaderAssetThinger.loadFromLuaScript(Path("resources/assets/shader/scene.lua"))
		val v = ShaderAssetThinger.compileSPIRV(srcs.vertex, Shaderc.shaderc_glsl_vertex_shader)
		val f = ShaderAssetThinger.compileSPIRV(srcs.fragment, Shaderc.shaderc_glsl_fragment_shader)
		return listOf(
			createShaderModule(VK_SHADER_STAGE_VERTEX_BIT, v),
			createShaderModule(VK_SHADER_STAGE_FRAGMENT_BIT, f),
		)
	}
	fun createHostVisibleBuffs(
		buffSize: Long, numBuffs: Int, usage: Int,
		id: String, layout: DescriptorLayout
	): List<GPUBuffer>
	{
		descAllocator.addDescSets(id, layout, numBuffs)
		val layoutInfo: DescriptorLayout.Info = layout.layoutInfos.first()
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
				device,
				r,
				r.requestedSize,
				layoutInfo.binding,
				layoutInfo.descType.vk,
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
		val buff = GPUBuffer(
			this,
			buffSize,
			usage,
			VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
			VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
			VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
		)
		val descSet = descAllocator.addDescSets(id, layout, 1).first()
		val first = layout.layoutInfos.first()
		descSet.setBuffer(
			this.device,
			buff,
			buff.requestedSize,
			first.binding,
			first.descType.vk
		)
		return buff
	}

	fun free()
	{
		device.waitIdle()
		textureManager.free()

		descLayoutVtxUniform.free()
		descLayoutTexture.free()
		shaderMatrixBuffer.freeAll()

		textureSampler.free()
		descAllocator.free()
		pipeline.cleanup()
		renderInfo.forEach(VkRenderingInfo::free)
		attInfoColor.forEach(VkRenderingAttachmentInfo.Buffer::free)
		attInfoDepth.forEach(VkRenderingAttachmentInfo::free)
		attDepth.forEach(Attachment::free)
		clrValueColor.free()
		clrValueDepth.free()

		meshManager.free()
		renderCompleteSemphs.forEach(Semaphore::free)
		swapChainDirectors.forEach(SwapChainDirector::free)

		pipelineCache.free(this)
		swapChain.cleanup(device)
		displaySurface.free(instance)
		device.free()
		hardware.free()
		instance.close()
	}
}