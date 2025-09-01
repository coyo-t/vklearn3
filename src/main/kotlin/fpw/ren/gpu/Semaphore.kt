package fpw.ren.gpu

import fpw.Renderer
import org.lwjgl.vulkan.VK10.vkDestroySemaphore


class Semaphore (val vkSemaphore: Long)
{
	fun free (context: Renderer)
	{
		vkDestroySemaphore(context.vkDevice, vkSemaphore, null)
	}
}