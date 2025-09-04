package fpw.ren.image

import fpw.ren.GPUtil
import fpw.ren.device.GPUDevice
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkImageViewCreateInfo

class ImageView (
	val device: GPUDevice,
	val vkImage: Long,
	imageViewData: Data,
	val isDepthImage: Boolean,
)
{
	val aspectMask = imageViewData.aspectMask
	val mipLevels = imageViewData.mipLevels
	val vkImageView = MemoryStack.stackPush().use { stack ->
		val lp = stack.mallocLong(1)
		val viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
		viewCreateInfo.`sType$Default`()
		viewCreateInfo.image(vkImage)
		viewCreateInfo.viewType(imageViewData.viewType)
		viewCreateInfo.format(imageViewData.format)
		viewCreateInfo.subresourceRange {
			it.aspectMask(aspectMask)
			it.baseMipLevel(0)
			it.levelCount(mipLevels)
			it.baseArrayLayer(imageViewData.baseArrayLayer)
			it.layerCount(imageViewData.layerCount)
		}

		GPUtil.gpuCheck(
			VK10.vkCreateImageView(device.logicalDevice.vkDevice, viewCreateInfo, null, lp),
			"image view creation"
		)
		lp.get(0)
	}


	fun free ()
	{
		VK10.vkDestroyImageView(device.logicalDevice.vkDevice, vkImageView, null)
	}

	data class Data(
		val aspectMask: Int = 0,
		val baseArrayLayer: Int = 0,
		val format: Int = 0,
		val layerCount: Int = 1,
		val mipLevels: Int = 1,
		val viewType: Int = VK10.VK_IMAGE_VIEW_TYPE_2D,
	)
}