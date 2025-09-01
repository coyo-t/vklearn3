package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkImageViewCreateInfo


class ImageView (
	device: LogicalDevice,
	vkImage: Long,
	imageViewData: ImageViewData,
	isDepthImage: Boolean,
)
{
	val vkImage = vkImage
	val aspectMask = imageViewData.aspectMask
	val mipLevels = imageViewData.mipLevels
	val isDepthImage = isDepthImage
	val vkImageView = MemoryStack.stackPush().use { stack ->
		val lp = stack.mallocLong(1)
		val viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
			.`sType$Default`()
			.image(vkImage)
			.viewType(imageViewData.viewType)
			.format(imageViewData.format)
			.subresourceRange {
				it
					.aspectMask(aspectMask)
					.baseMipLevel(0)
					.levelCount(mipLevels)
					.baseArrayLayer(imageViewData.baseArrayLayer)
					.layerCount(imageViewData.layerCount)
			}

		gpuCheck(
			vkCreateImageView(device.vkDevice, viewCreateInfo, null, lp),
			"image view creation"
		)
		lp.get(0)
	}


	fun free (device: LogicalDevice)
	{
		vkDestroyImageView(device.vkDevice, vkImageView, null)
	}

}