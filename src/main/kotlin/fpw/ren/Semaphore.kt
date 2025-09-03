package fpw.ren

import fpw.Renderer
import fpw.ren.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.vkCreateSemaphore
import org.lwjgl.vulkan.VK10.vkDestroySemaphore
import org.lwjgl.vulkan.VkSemaphoreCreateInfo
import kotlin.use


class Semaphore (
	val context: Renderer,
)
{
	val vkSemaphore: Long
	init
	{
		MemoryStack.stackPush().use { stack ->
			val semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack).`sType$Default`()
			val lp = stack.mallocLong(1)
			gpuCheck(
				vkCreateSemaphore(context.gpu.logicalDevice.vkDevice, semaphoreCreateInfo, null, lp),
				"Failed to create semaphore"
			)
			vkSemaphore = lp[0]
		}
	}

	fun free ()
	{
		vkDestroySemaphore(context.gpu.logicalDevice.vkDevice, vkSemaphore, null)
	}
}