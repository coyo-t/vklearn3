package fpw.ren

import fpw.Renderer
import fpw.ren.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRSurface
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT
import org.lwjgl.vulkan.VK10.VK_TRUE
import org.lwjgl.vulkan.VK13
import org.lwjgl.vulkan.VkCommandBufferSubmitInfo
import org.lwjgl.vulkan.VkQueue
import org.lwjgl.vulkan.VkSemaphoreSubmitInfo
import org.lwjgl.vulkan.VkSubmitInfo2

open class CommandQueue (
	val vkCtx: Renderer,
	val queueFamilyIndex: Int,
	queueIndex: Int,
)
{
	val vkQueue: VkQueue

	init
	{
		MemoryStack.stackPush().use { stack ->
			val pQueue = stack.mallocPointer(1)
			VK10.vkGetDeviceQueue(vkCtx.gpu.logicalDevice.vkDevice, queueFamilyIndex, queueIndex, pQueue)
			val queue = pQueue.get(0)
			vkQueue = VkQueue(queue, vkCtx.gpu.logicalDevice.vkDevice)
		}
	}

	fun waitIdle()
	{
		VK10.vkQueueWaitIdle(vkQueue)
	}

	fun submit(
		commandBuffers: VkCommandBufferSubmitInfo.Buffer?,
		waitSemaphores: VkSemaphoreSubmitInfo.Buffer?,
		signalSemaphores: VkSemaphoreSubmitInfo.Buffer?,
		fence: GPUFence?
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
			val fenceHandle = fence?.vkFence ?: VK10.VK_NULL_HANDLE
			gpuCheck(
				VK13.vkQueueSubmit2(vkQueue, submitInfo, fenceHandle),
				"Failed to submit command to queue",
			)
		}
	}

	companion object
	{
		fun createPresentation (r: Renderer, q: Int)
			= CommandQueue(r, r.getPresentQueueFamilyIndex(), q)

		fun createGraphics (r: Renderer, q: Int)
			= CommandQueue(r, r.getGraphicsQueueFamilyIndex(), q)

		internal fun Renderer.getGraphicsQueueFamilyIndex(): Int
		{
			val queuePropsBuff = gpu.hardwareDevice.vkQueueFamilyProps
			val uhh = queuePropsBuff.indexOfFirst { (it.queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0 }
			require(uhh >= 0) {
				"Failed to get graphics Queue family index"
			}
			return uhh
		}

		internal fun Renderer.getPresentQueueFamilyIndex(): Int
		{
			MemoryStack.stackPush().use { stack ->
				val queuePropsBuff = gpu.hardwareDevice.vkQueueFamilyProps
				val numQueuesFamilies: Int = queuePropsBuff.capacity()
				val intBuff = stack.mallocInt(1)
				for (i in 0..<numQueuesFamilies)
				{
					KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(
						gpu.hardwareDevice.vkPhysicalDevice,
						i, displaySurface.vkSurface, intBuff
					)
					if (intBuff.get(0) == VK_TRUE)
					{
						return i
					}
				}
			}
			throw RuntimeException("Failed to get Presentation Queue family index")
		}
	}

}