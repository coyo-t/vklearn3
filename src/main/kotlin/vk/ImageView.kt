package com.catsofwar.vk

import com.catsofwar.vk.VKUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkImageViewCreateInfo


class ImageView (
	device: Device,
	val vkImage: Long,
	imageViewData: ImageViewData,
)
{
	val aspectMask = imageViewData.aspectMask
	val mipLevels = imageViewData.mipLevels
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

		vkCheck(
			vkCreateImageView(device.vkDevice, viewCreateInfo, null, lp),
			"Failed to create image view"
		)
		lp.get(0)
	}


	fun cleanup(device: Device)
	{
		vkDestroyImageView(device.vkDevice, vkImageView, null)
	}

	data class ImageViewData(
		val aspectMask: Int = 0,
		val baseArrayLayer: Int = 0,
		val format: Int = 0,
		val layerCount: Int = 1,
		val mipLevels: Int = 1,
		val viewType: Int = VK_IMAGE_VIEW_TYPE_2D,
	)

}