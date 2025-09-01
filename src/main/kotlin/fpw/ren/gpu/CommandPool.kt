package fpw.ren.gpu

import fpw.Renderer
import org.lwjgl.vulkan.VK10.*

class CommandPool (val vkCommandPool: Long)
{
	fun reset (vkCtx: Renderer)
	{
		vkResetCommandPool(vkCtx.device.vkDevice, vkCommandPool, 0)
	}

	fun free (vkCtx: Renderer)
	{
		vkDestroyCommandPool(vkCtx.device.vkDevice, vkCommandPool, null)
	}
}