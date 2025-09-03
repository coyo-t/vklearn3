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
import org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR
import org.lwjgl.vulkan.VK10.VK_FORMAT_D16_UNORM
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkShaderModuleCreateInfo.calloc
import party.iroiro.luajava.value.LuaTableValue
import java.awt.Color
import java.nio.ByteBuffer


class Renderer (val engineContext: Engine)
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
		device,
		engineContext.window.wide,
		engineContext.window.tall,
		displaySurface,
		preferredImageBufferingCount,
		useVerticalSync,
	)

	val vkDevice get() = device.vkDevice

	val pipelineCache = createPipelineCache()

	var currentFrame = 0
		private set

	val graphicsQueue = CommandQueue.createGraphics(this, 0)
	val presentQueue = CommandQueue.createPresentation(this, 0)

	val swapChainDirectors = List(maxInFlightFrameCount) {
		SwapChainDirector(this)
	}

	var renderCompleteSemphs = List(swapChain.numImages) {
		Semaphore(this)
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
		LuaCoyote().use { L ->
			L.openLibraries()
			val thing = (L.run(engineContext.testModel) as? LuaTableValue) ?: return@use
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
				.semaphore(renderCompleteSemphs[imageIndex].vkSemaphore)
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
			val swapChain = swapChain
			val swapChainVisualImage = swapChain.imageViews[imageIndex].vkImage
			val swapChainDepthImage = attDepth[imageIndex].image.vkImage
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
			val renInf = renderInfo[imageIndex]
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
			val swapChainExtent = swapChain.extents
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

		doResize = swapChain.presentImage(presentQueue, renderCompleteSemphs[imageIndex], imageIndex)

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
		device.waitIdle()

		swapChain.free()
		displaySurface.free(instance)
		displaySurface = DisplaySurface(instance, hardware, window)
		swapChain = SwapChain(
			device,
			window.wide,
			window.tall,
			displaySurface,
			preferredImageBufferingCount,
			useVerticalSync,
		)

		swapChainDirectors.forEach(SwapChainDirector::onResize)

		renderCompleteSemphs.forEach { it.free() }
		renderCompleteSemphs = List(swapChain.numImages) {
			Semaphore(this)
		}

		val extent = swapChain.extents
		engineContext.lens.resize(extent.width(), extent.height())

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
			val inf = VkRenderingAttachmentInfo.calloc(1)
			inf.`sType$Default`()
			inf.imageView(swapChain.imageViews[it].vkImageView)
			inf.imageLayout(VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR)
			inf.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
			inf.storeOp(VK_ATTACHMENT_STORE_OP_STORE)
			inf.clearValue(clearValue)
			inf
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
				val inf = VkRenderingInfo.calloc()
				inf.`sType$Default`()
				inf.renderArea(renderArea)
				inf.layerCount(1)
				inf.pColorAttachments(colorAttachments[it])
				inf.pDepthAttachment(depthAttachments[it])
				inf
			}
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
			val inf = VkRenderingAttachmentInfo.calloc()
			inf.`sType$Default`()
			inf.imageView(depthAttachments[it].imageView.vkImageView)
			inf.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
			inf.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
			inf.storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
			inf.clearValue(clearValue)
			inf
		}
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
				device,
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
			device,
			buff,
			buff.requestedSize,
			first.binding,
			first.descType.vk
		)
		return buff
	}

	fun createPipelineCache(): PipelineCache
	{
		val outs = MemoryStack.stackPush().use { stack ->
			val createInfo = VkPipelineCacheCreateInfo.calloc(stack).`sType$Default`()
			val lp = stack.mallocLong(1)
			gpuCheck(
				vkCreatePipelineCache(device.vkDevice, createInfo, null, lp),
				"Error creating pipeline cache"
			)
			lp.get(0)
		}
		return PipelineCache(this, outs)
	}

	fun free()
	{
		device.waitIdle()
		textureManager.free()

		descriptorLayoutVertexStage.free()
//		descLayoutTexture.free()
		shaderMatrixBuffer.forEach { it.free() }

//		textureSampler.free()
		descAllocator.free()
		pipeline.free()
		renderInfo.forEach { it.free() }
		attInfoColor.forEach { it.free() }
		attInfoDepth.forEach { it.free() }
		attDepth.forEach { it.free() }
		clrValueColor.free()
		clrValueDepth.free()

		meshManager.free()
		renderCompleteSemphs.forEach { it.free() }
		swapChainDirectors.forEach { it.free() }

		pipelineCache.free()
		swapChain.free()
		displaySurface.free(instance)
		memAlloc.free()
		device.free()
		hardware.free()
		instance.close()
	}
}