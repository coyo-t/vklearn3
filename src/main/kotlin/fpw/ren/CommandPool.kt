package fpw.ren

import fpw.Renderer
import org.lwjgl.vulkan.VK10.*

class CommandPool (
	val vkCtx: Renderer,
	val vkCommandPool: Long,
)
{
	fun reset ()
	{
		vkResetCommandPool(vkCtx.device.vkDevice, vkCommandPool, 0)
	}

	fun free ()
	{
		vkDestroyCommandPool(vkCtx.device.vkDevice, vkCommandPool, null)
	}
}