package fpw.ren.gpu

import org.lwjgl.vulkan.VK10.*

class CommandPool (val vkCommandPool: Long)
{
	fun reset (vkCtx: GPUContext)
	{
		vkResetCommandPool(vkCtx.device.vkDevice, vkCommandPool, 0)
	}

	fun free (vkCtx: GPUContext)
	{
		vkDestroyCommandPool(vkCtx.device.vkDevice, vkCommandPool, null)
	}
}