package fpw.ren

import fpw.ren.image.ImageView
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkRect2D
import org.lwjgl.vulkan.VkRenderingAttachmentInfo
import org.lwjgl.vulkan.VkRenderingInfo

class FrameDataz(
	val renderer: Renderer,
	val swapChain: SwapChain,
	val imageView: ImageView,
)
{
	val renderCompleteFlag = Semaphore(renderer)

	val colorInfo = run {
		val f = VkRenderingAttachmentInfo.calloc(1)
		f.`sType$Default`()
		f.imageView(imageView.vkImageView)
		f.imageLayout(VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR)
		f.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
		f.storeOp(VK_ATTACHMENT_STORE_OP_STORE)
		f.clearValue(renderer.clrValueColor)
		GPUtil.registerForCleanup(f)
	}

	val depthAttachment = Attachment(
		renderer,
		swapChain.wide,
		swapChain.tall,
//				VK_FORMAT_D32_SFLOAT,
		VK_FORMAT_D16_UNORM,
		VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
	)

	val depthInfo = run {
		val f = VkRenderingAttachmentInfo.calloc()
		f.`sType$Default`()
		f.imageView(depthAttachment.imageView.vkImageView)
		f.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
		f.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
		f.storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
		f.clearValue(renderer.clrValueDepth)
		GPUtil.registerForCleanup(f)
	}

	val renderInfo = run {
		stackPush().use { stack ->
			val extent = swapChain.extents
			val renderArea = VkRect2D.calloc(stack)
			renderArea.extent(extent)

			val f = VkRenderingInfo.calloc()
			f.`sType$Default`()
			f.renderArea(renderArea)
			f.layerCount(1)
			f.pColorAttachments(colorInfo)
			f.pDepthAttachment(depthInfo)
			GPUtil.registerForCleanup(f)
		}
	}

	fun free ()
	{
		renderCompleteFlag.free()

		depthAttachment.free()
		imageView.free()
	}
}