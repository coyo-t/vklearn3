package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkFenceCreateInfo


class GPUFence
private constructor (val vkFence: Long): GPUClosable
{
	companion object
	{
		operator fun invoke (vkCtx: GPUContext, signaled: Boolean): GPUFence
		{
			MemoryStack.stackPush().use { stack ->
				val fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
					.`sType$Default`()
					.flags(if (signaled) VK_FENCE_CREATE_SIGNALED_BIT else 0)
				val lp = stack.mallocLong(1)
				vkCheck(vkCreateFence(vkCtx.vkDevice, fenceCreateInfo, null, lp), "Failed to create fence")
				return GPUFence(lp.get(0))
			}
		}
	}

	override fun close (context: GPUContext)
	{
		vkDestroyFence(context.device.vkDevice, vkFence, null)
	}

	fun fenceWait(vkCtx: GPUContext)
	{
		vkWaitForFences(vkCtx.device.vkDevice, vkFence, true, Long.MAX_VALUE)
	}

	fun reset(vkCtx: GPUContext)
	{
		vkResetFences(vkCtx.device.vkDevice, vkFence)
	}

}
