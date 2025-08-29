package fpw.ren

import fpw.EngineContext
import fpw.FUtil
import fpw.ren.gpu.*
import fpw.ren.gpu.GPUtil.imageBarrier
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR
import org.lwjgl.vulkan.VK10.vkCmdPushConstants
import org.lwjgl.vulkan.VK13.*
import java.util.*
import java.util.function.Consumer
import kotlin.io.path.Path


class SceneRender (vkCtx: GPUContext): GPUClosable
{
	private val clrValueColor = VkClearValue.calloc().color {
		it.float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1.0f)
	}
	private val clrValueDepth = VkClearValue.calloc().color {
		it.float32(0, 1f)
	}
	var attDepth = createDepthAttachments(vkCtx)
	var attInfoColor = createColorAttachmentsInfo(vkCtx, clrValueColor)
	var attInfoDepth = createDepthAttachmentsInfo(vkCtx, attDepth, clrValueDepth)
	private var renderInfo = createRenderInfo(vkCtx, attInfoColor, attInfoDepth)
	private val pipeline = run {
		val shaderModules = createShaderModules(vkCtx)
		createPipeline(vkCtx, shaderModules).also { shaderModules.forEach { it.close(vkCtx) } }
	}
	private val pushConstantsBuffer = FUtil.createBuffer(128)

	private fun createColorAttachmentsInfo(
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

	private fun createRenderInfo(
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

	override fun close (context: GPUContext)
	{
		pipeline.cleanup(context)
		renderInfo.forEach { it.free() }
		attInfoColor.forEach { it.free() }
		attInfoDepth.forEach { it.free() }
		attDepth.forEach { it.close(context) }
		clrValueColor.free()
		clrValueDepth.free()
	}

	fun render (
		engineContext: EngineContext,
		vkCtx: GPUContext,
		cmdBuffer: GPUCommandBuffer,
		modelsCache: ModelsCache,
		imageIndex: Int,
	)
	{
		MemoryStack.stackPush().use { stack ->
			val swapChain = vkCtx.swapChain
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
					.extent(Consumer { it: VkExtent2D? -> it!!.width(width).height(height) })
					.offset(Consumer { it: VkOffset2D? -> it!!.x(0).y(0) })
				vkCmdSetScissor(cmdHandle, 0, scissor)

				val offsets = stack.mallocLong(1).put(0, 0L)
				val vertexBuffer = stack.mallocLong(1)

				val scene = engineContext.scene
				val entities = scene.entities
				val numEntities = entities.size
				for (i in 0..<numEntities)
				{
					val entity = entities[i]
					val model = modelsCache.modelMap[entity.modelId] ?: continue
					val vulkanMeshList = model.vulkanMeshList
					val numMeshes = vulkanMeshList.size
					setPushConstants(cmdHandle, scene.projection.projectionMatrix, entity.modelMatrix)
					for (j in 0..<numMeshes)
					{
						val vulkanMesh = vulkanMeshList[j]
						vertexBuffer.put(0, vulkanMesh.verticesBuffer.buffer)
						vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets!!)
						vkCmdBindIndexBuffer(cmdHandle, vulkanMesh.indicesBuffer.buffer, 0, VK_INDEX_TYPE_UINT32)
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
	}

	private fun setPushConstants (cmdHandle: VkCommandBuffer, projMatrix: Matrix4f, modelMatrix: Matrix4f)
	{
		projMatrix.get(pushConstantsBuffer)
		modelMatrix.get(GPUtil.SIZEOF_MAT4, pushConstantsBuffer)
		vkCmdPushConstants(cmdHandle, pipeline.vkPipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantsBuffer)
	}
	fun resize(vkCtx: GPUContext)
	{
		renderInfo.forEach { it.free() }
		attInfoDepth.forEach { it.free() }
		attInfoColor.forEach { it.free() }
		attDepth.forEach { it.close(vkCtx) }

		attDepth = createDepthAttachments(vkCtx)
		attInfoColor = createColorAttachmentsInfo(vkCtx, clrValueColor)
		attInfoDepth = createDepthAttachmentsInfo(vkCtx, attDepth, clrValueDepth)
		renderInfo = createRenderInfo(vkCtx, attInfoColor, attInfoDepth)
	}
	companion object
	{
		private fun createDepthAttachments(vkCtx: GPUContext): List<Attachment>
		{
			val swapChain = vkCtx.swapChain
			val numImages: Int = swapChain.numImages
			val swapChainExtent: VkExtent2D = swapChain.swapChainExtent
			return List(numImages) {
				Attachment(
					vkCtx,
					swapChainExtent.width(),
					swapChainExtent.height(),
					VK_FORMAT_D16_UNORM,
					VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
				)
			}
		}
		private fun createDepthAttachmentsInfo(
			vkCtx: GPUContext,
			depthAttachments: List<Attachment>,
			clearValue: VkClearValue
		): List<VkRenderingAttachmentInfo>
		{
			val swapChain = vkCtx.swapChain
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

		private fun createPipeline (vkCtx: GPUContext, shaderModules: List<GPUShaderModule>): GPUPipeLine
		{
			GPUVertexBufferStruct().use { vtxBuffStruct ->
				val buildInfo = GPUPipeLineBuildInfo(
						shaderModules = shaderModules,
						vi = vtxBuffStruct.vi,
						colorFormat = vkCtx.surface.surfaceFormat.imageFormat,
						depthFormat = VK_FORMAT_D16_UNORM,
						pushConstRange = listOf(
							PushConstantRange(VK_SHADER_STAGE_VERTEX_BIT, 0, 128)
						)
					)
				return GPUPipeLine(vkCtx, buildInfo)
			}
		}

		private fun createShaderModules(vkCtx: GPUContext): List<GPUShaderModule>
		{
			val srcs = ShaderAssetThinger.loadFromLuaScript(Path("resources/assets/shader/scene.lua"))
			val v = ShaderAssetThinger.compileSPIRV(srcs.vertex, Shaderc.shaderc_glsl_vertex_shader)
			val f = ShaderAssetThinger.compileSPIRV(srcs.fragment, Shaderc.shaderc_glsl_fragment_shader)
			return listOf(
				GPUShaderModule.create(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, v),
				GPUShaderModule.create(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, f),
			)
		}
	}

}