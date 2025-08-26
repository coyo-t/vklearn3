package com.catsofwar.vk

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkQueue
import org.tinylog.kotlin.Logger


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

}