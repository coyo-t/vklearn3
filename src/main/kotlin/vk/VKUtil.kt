package com.catsofwar.vk

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK13.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkDependencyInfo
import org.lwjgl.vulkan.VkImageMemoryBarrier2


object VKUtil
{
	enum class OSType
	{
		WINDOWZ,
		LINUXZ,
		MACINTOSHZ,
		SOLARIZ,
		DUDE_IDFK,
		;

		companion object
		{
			val isMacintosh
				get() = get() == MACINTOSHZ

			fun get (): OSType
			{
				val os = System.getProperty("os.name", "generic").lowercase()
				if ((os.indexOf("mac") >= 0) || (os.indexOf("darwin") >= 0))
				{
					return MACINTOSHZ
				}
				else if (os.indexOf("win") >= 0)
				{
					return WINDOWZ
				}
				else if (os.indexOf("nux") >= 0)
				{
					return LINUXZ
				}
				return DUDE_IDFK
			}
		}
	}

	fun imageBarrier(
		stack: MemoryStack,
		cmdHandle: VkCommandBuffer,
		image: Long,
		oldLayout: Int,
		newLayout: Int,
		srcStage: Long,
		dstStage: Long,
		srcAccess: Long,
		dstAccess: Long,
		aspectMask: Int,
	)
	{
		val imageBarrier = VkImageMemoryBarrier2.calloc(1, stack)
			.`sType$Default`()
			.oldLayout(oldLayout)
			.newLayout(newLayout)
			.srcStageMask(srcStage)
			.dstStageMask(dstStage)
			.srcAccessMask(srcAccess)
			.dstAccessMask(dstAccess)
			.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
			.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
			.subresourceRange {
				it
					.aspectMask(aspectMask)
					.baseMipLevel(0)
					.levelCount(VK_REMAINING_MIP_LEVELS)
					.baseArrayLayer(0)
					.layerCount(VK_REMAINING_ARRAY_LAYERS)
			}
			.image(image)

		val depInfo = VkDependencyInfo.calloc(stack)
			.`sType$Default`()
			.pImageMemoryBarriers(imageBarrier)

		vkCmdPipelineBarrier2(cmdHandle, depInfo)
	}

	fun vkCheck(err: Int, errMsg: String?)
	{
		if (err != VK_SUCCESS)
		{
			val errCode = when (err)
			{
				VK_NOT_READY -> "VK_NOT_READY"
				VK_TIMEOUT -> "VK_TIMEOUT"
				VK_EVENT_SET -> "VK_EVENT_SET"
				VK_EVENT_RESET -> "VK_EVENT_RESET"
				VK_INCOMPLETE -> "VK_INCOMPLETE"
				VK_ERROR_OUT_OF_HOST_MEMORY -> "VK_ERROR_OUT_OF_HOST_MEMORY"
				VK_ERROR_OUT_OF_DEVICE_MEMORY -> "VK_ERROR_OUT_OF_DEVICE_MEMORY"
				VK_ERROR_INITIALIZATION_FAILED -> "VK_ERROR_INITIALIZATION_FAILED"
				VK_ERROR_DEVICE_LOST -> "VK_ERROR_DEVICE_LOST"
				VK_ERROR_MEMORY_MAP_FAILED -> "VK_ERROR_MEMORY_MAP_FAILED"
				VK_ERROR_LAYER_NOT_PRESENT -> "VK_ERROR_LAYER_NOT_PRESENT"
				VK_ERROR_EXTENSION_NOT_PRESENT -> "VK_ERROR_EXTENSION_NOT_PRESENT"
				VK_ERROR_FEATURE_NOT_PRESENT -> "VK_ERROR_FEATURE_NOT_PRESENT"
				VK_ERROR_INCOMPATIBLE_DRIVER -> "VK_ERROR_INCOMPATIBLE_DRIVER"
				VK_ERROR_TOO_MANY_OBJECTS -> "VK_ERROR_TOO_MANY_OBJECTS"
				VK_ERROR_FORMAT_NOT_SUPPORTED -> "VK_ERROR_FORMAT_NOT_SUPPORTED"
				VK_ERROR_FRAGMENTED_POOL -> "VK_ERROR_FRAGMENTED_POOL"
				VK_ERROR_UNKNOWN -> "VK_ERROR_UNKNOWN"
				else -> "Not mapped"
			}
			throw RuntimeException("$errMsg: $errCode [$err]")
		}
	}
}