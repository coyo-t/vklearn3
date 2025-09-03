package fpw.ren

import fpw.Renderer
import org.lwjgl.vulkan.VK10.vkDestroySemaphore


class Semaphore (val context: Renderer, val vkSemaphore: Long)
{
	fun free ()
	{
		vkDestroySemaphore(context.vkDevice, vkSemaphore, null)
	}
}