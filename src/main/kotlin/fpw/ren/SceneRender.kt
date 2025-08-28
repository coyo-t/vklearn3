package fpw.ren

import fpw.ren.gpu.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR
import org.lwjgl.vulkan.VK13.*
import java.util.function.Consumer
import kotlin.io.path.Path


class SceneRender (vkCtx: GPUContext): GPUClosable
{

	private val clrValueColor = VkClearValue.calloc().color { c ->
		c.float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1.0f)
	}
	private val attInfoColor = createColorAttachmentsInfo(vkCtx, clrValueColor)
	private val renderInfo = createRenderInfo(vkCtx, attInfoColor)
	private val pipeline = run {
		val shaderModules = createShaderModules(vkCtx)
		createPipeline(vkCtx, shaderModules).also { shaderModules.forEach { it.close(vkCtx) } }
	}

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
		attachments: List<VkRenderingAttachmentInfo.Buffer>
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
					.pColorAttachments(attachments[it])

			}
		}
	}

	override fun close (context: GPUContext)
	{
		pipeline.cleanup(context)
		renderInfo.forEach(VkRenderingInfo::free)
		attInfoColor.forEach(VkRenderingAttachmentInfo.Buffer::free)
		clrValueColor.free()
	}

	fun render (
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

			GPUtil.imageBarrier(
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

			GPUtil.renderScoped(cmdHandle, renderInfo[imageIndex]) {
				vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.vkPipeline)
				val swapChainExtent = swapChain.swapChainExtent
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
				val vulkanModels= modelsCache.modelMap.values
				for (vulkanModel in vulkanModels)
				{
					for (mesh in vulkanModel.vulkanMeshList)
					{
						vertexBuffer.put(0, mesh.verticesBuffer.buffer)
						vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets!!)
						vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer.buffer, 0, VK_INDEX_TYPE_UINT32)
						vkCmdDrawIndexed(cmdHandle, mesh.numIndices, 1, 0, 0, 0)
					}
				}
			}
			GPUtil.imageBarrier(
				stack,
				cmdHandle,
				swapChainImage,
				VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
				VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
				VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
				VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
				(
					VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT or
					VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT or
					0
				),
				VK_PIPELINE_STAGE_2_NONE,
				VK_IMAGE_ASPECT_COLOR_BIT,
			)
		}
	}

	companion object
	{
		private fun createPipeline (vkCtx: GPUContext, shaderModules: List<GPUShaderModule>): GPUPipeLine
		{
			val vtxBuffStruct = GPUVertexBufferStruct()
			val buildInfo = GPUPipeLineBuildInfo(
				colorFormat = vkCtx.surface.surfaceFormat.imageFormat,
				shaderModules = shaderModules,
				vi = vtxBuffStruct.vi,
			)
			val pipeline = GPUPipeLine(vkCtx, buildInfo)
			vtxBuffStruct.cleanup()
			return pipeline
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