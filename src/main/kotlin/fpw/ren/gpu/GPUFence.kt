package fpw.ren.gpu

import org.lwjgl.vulkan.VK10.*


class GPUFence(val vkFence: Long)
{
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
