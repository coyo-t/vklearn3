package fpw.ren

import fpw.Renderer
import org.lwjgl.vulkan.VK10.*


class GPUFence(val vkFence: Long)
{
	fun free (context: Renderer)
	{
		vkDestroyFence(context.vkDevice, vkFence, null)
	}

	fun wait(vkCtx: Renderer)
	{
		vkWaitForFences(vkCtx.vkDevice, vkFence, true, Long.MAX_VALUE)
	}

	fun reset(vkCtx: Renderer)
	{
		vkResetFences(vkCtx.vkDevice, vkFence)
	}
}
