package com.catsofwar.vk

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR
import org.lwjgl.vulkan.VK13.*
import java.util.*
import java.util.function.Consumer


class ScnRender (vkCtx: VKContext): AutoCloseable
{

	private val clrValueColor = VkClearValue.calloc().color { c ->
		c.float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1.0f)
	}
	private val attInfoColor = createColorAttachmentsInfo(vkCtx, clrValueColor)
	private val renderInfo = createRenderInfo(vkCtx, attInfoColor)

	private fun createColorAttachmentsInfo(
		vkCtx: VKContext,
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
		vkCtx: VKContext,
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

	override fun close ()
	{
		renderInfo.forEach(VkRenderingInfo::free)
		attInfoColor.forEach(VkRenderingAttachmentInfo.Buffer::free)
		clrValueColor.free()
	}

	fun render (vkCtx: VKContext, cmdBuffer: CommandBuffer, imageIndex: Int)
	{
		MemoryStack.stackPush().use { stack ->
			val swapChain = vkCtx.swapChain
			val swapChainImage = swapChain.imageViews[imageIndex].vkImage
			val cmdHandle = cmdBuffer.vkCommandBuffer

			VKUtil.imageBarrier(
				stack, cmdHandle, swapChainImage,
				VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
				VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
				VK_ACCESS_2_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
				VK_IMAGE_ASPECT_COLOR_BIT
			)

			vkCmdBeginRendering(cmdHandle, renderInfo[imageIndex])

			vkCmdEndRendering(cmdHandle)
			VKUtil.imageBarrier(
				stack, cmdHandle, swapChainImage,
				VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
				VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
				VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_PIPELINE_STAGE_2_NONE,
				VK_IMAGE_ASPECT_COLOR_BIT
			)
		}
	}

}