package fpw.ren

import fpw.Renderer
import fpw.ren.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkFenceCreateInfo
import kotlin.use


class Fence (
	val context: Renderer,
	signaled: Boolean = true
)
{
	val vkFence: Long
	init
	{
		MemoryStack.stackPush().use { stack ->
			val fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
				.`sType$Default`()
				.flags(if (signaled) VK_FENCE_CREATE_SIGNALED_BIT else 0)
			val lp = stack.mallocLong(1)
			gpuCheck(
				vkCreateFence(context.gpu.logicalDevice.vkDevice, fenceCreateInfo, null, lp),
				"Failed to create fence",
			)
			vkFence = lp[0]
		}
	}


	fun free ()
	{
		vkDestroyFence(context.gpu.logicalDevice.vkDevice, vkFence, null)
	}

	fun waitForFences()
	{
		vkWaitForFences(context.gpu.logicalDevice.vkDevice, vkFence, true, Long.MAX_VALUE)
	}

	fun reset()
	{
		vkResetFences(context.gpu.logicalDevice.vkDevice, vkFence)
	}
}
