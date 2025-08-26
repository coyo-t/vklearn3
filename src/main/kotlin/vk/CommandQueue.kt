package com.catsofwar.vk

import com.catsofwar.vk.VKUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VK13.vkQueueSubmit2
import org.tinylog.kotlin.Logger
import java.util.*


sealed class CommandQueue (vkCtx: VKContext, queueFamilyIndex: Int, queueIndex: Int)
{
	val queueFamilyIndex: Int
	val vkQueue: VkQueue

	init
	{
		Logger.debug("Creating queue")

		this.queueFamilyIndex = queueFamilyIndex
		MemoryStack.stackPush().use { stack ->
			val pQueue = stack.mallocPointer(1)
			vkGetDeviceQueue(vkCtx.device.vkDevice, queueFamilyIndex, queueIndex, pQueue)
			val queue = pQueue.get(0)
			vkQueue = VkQueue(queue, vkCtx.device.vkDevice)
		}
	}

	fun waitIdle()
	{
		vkQueueWaitIdle(vkQueue)
	}

	fun submit(
		commandBuffers: VkCommandBufferSubmitInfo.Buffer?,
		waitSemaphores: VkSemaphoreSubmitInfo.Buffer?,
		signalSemaphores: VkSemaphoreSubmitInfo.Buffer?,
		fence: Fence?
	)
	{
		MemoryStack.stackPush().use { stack ->
			val submitInfo = VkSubmitInfo2.calloc(1, stack)
				.`sType$Default`()
				.pCommandBufferInfos(commandBuffers)
				.pSignalSemaphoreInfos(signalSemaphores)
			if (waitSemaphores != null)
			{
				submitInfo.pWaitSemaphoreInfos(waitSemaphores)
			}
			val fenceHandle = fence?.vkFence ?: VK_NULL_HANDLE
			vkCheck(vkQueueSubmit2(vkQueue, submitInfo, fenceHandle), "Failed to submit command to queue")
		}
	}

	class GraphicsQueue(vkCtx: VKContext, queueIndex: Int):
		CommandQueue(vkCtx, getGraphicsQueueFamilyIndex(vkCtx), queueIndex)
	{
		companion object
		{
			private fun getGraphicsQueueFamilyIndex(vkCtx: VKContext): Int
			{
				val queuePropsBuff = vkCtx.physDevice.vkQueueFamilyProps
				val uhh = queuePropsBuff.indexOfFirst { (it.queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0 }
//				var index = -1
//				val numQueuesFamilies = queuePropsBuff.capacity()
//				for (i in 0..<numQueuesFamilies)
//				{
//					val props = queuePropsBuff.get(i)
//					val graphicsQueue = (props.queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0
//					if (graphicsQueue)
//					{
//						index = i
//						break
//					}
//				}

				require(uhh >= 0) {
					"Failed to get graphics Queue family index"
				}
				return uhh
			}
		}
	}

	class PresentQueue (vkCtx: VKContext, queueIndex: Int):
		CommandQueue(vkCtx, getPresentQueueFamilyIndex(vkCtx), queueIndex)
	{
		companion object
		{
			private fun getPresentQueueFamilyIndex (vkCtx: VKContext): Int
			{
				var index = -1
				MemoryStack.stackPush().use { stack ->
					val queuePropsBuff = vkCtx.physDevice.vkQueueFamilyProps
					val numQueuesFamilies: Int = queuePropsBuff.capacity()
					val intBuff = stack.mallocInt(1)
					for (i in 0..<numQueuesFamilies)
					{
						KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(
							vkCtx.physDevice.vkPhysicalDevice,
							i, vkCtx.surface.vkSurface, intBuff
						)
						val supportsPresentation = intBuff.get(0) == VK_TRUE
						if (supportsPresentation)
						{
							index = i
							break
						}
					}
				}
				if (index < 0)
				{
					throw RuntimeException("Failed to get Presentation Queue family index")
				}
				return index
			}
		}
	}

}