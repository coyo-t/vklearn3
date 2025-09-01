package fpw.ren.gpu.queuez

import fpw.Renderer
import fpw.ren.gpu.GPUFence
import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VK13.vkQueueSubmit2


sealed class CommandQueue
{
	val queueFamilyIndex: Int
	val vkQueue: VkQueue

	constructor (vkCtx: Renderer, queueFamilyIndex: Int, queueIndex: Int)
	{

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
			val fenceHandle = fence?.vkFence ?: VK_NULL_HANDLE
			gpuCheck(vkQueueSubmit2(vkQueue, submitInfo, fenceHandle), "Failed to submit command to queue")
		}
	}

}