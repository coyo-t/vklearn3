package fpw.ren.gpu

import org.lwjgl.vulkan.VK10.vkDestroySemaphore


class Semaphore (val vkSemaphore: Long)
{
	fun free (context: GPUContext)
	{
		vkDestroySemaphore(context.vkDevice, vkSemaphore, null)
	}
}