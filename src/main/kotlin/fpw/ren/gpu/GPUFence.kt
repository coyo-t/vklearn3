package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkFenceCreateInfo


class GPUFence(val vkFence: Long)
{
	companion object
	{
		fun createzor (c: GPUContext, signaled: Boolean): GPUFence
		{
			MemoryStack.stackPush().use { stack ->
				val fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
					.`sType$Default`()
					.flags(if (signaled) VK_FENCE_CREATE_SIGNALED_BIT else 0)
				val lp = stack.mallocLong(1)
				vkCheck(vkCreateFence(c.vkDevice, fenceCreateInfo, null, lp), "Failed to create fence")
				return GPUFence(lp[0])
			}
		}
	}

	fun close (context: GPUContext)
	{
		vkDestroyFence(context.vkDevice, vkFence, null)
	}

	fun wait(vkCtx: GPUContext)
	{
		vkWaitForFences(vkCtx.vkDevice, vkFence, true, Long.MAX_VALUE)
	}

	fun reset(vkCtx: GPUContext)
	{
		vkResetFences(vkCtx.vkDevice, vkFence)
	}

}
