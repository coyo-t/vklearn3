package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.vkCreateSemaphore
import org.lwjgl.vulkan.VK10.vkDestroySemaphore
import org.lwjgl.vulkan.VkSemaphoreCreateInfo


@JvmInline
value class GPUSemaphore
private constructor (val vkSemaphore: Long): GPUClosable
{
	override fun close (context: GPUContext)
	{
		vkDestroySemaphore(context.vkDevice, vkSemaphore, null)
	}

	companion object
	{
		operator fun invoke (context: GPUContext): GPUSemaphore
		{
			MemoryStack.stackPush().use { stack ->
				val semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack).`sType$Default`()
				val lp = stack.mallocLong(1)
				vkCheck(
					vkCreateSemaphore(context.vkDevice, semaphoreCreateInfo, null, lp),
					"Failed to create semaphore"
				)
				return GPUSemaphore(lp.get(0))
			}
		}
	}
}