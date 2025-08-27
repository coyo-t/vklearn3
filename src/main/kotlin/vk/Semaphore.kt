package com.catsofwar.vk

import com.catsofwar.vk.VKUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.vkCreateSemaphore
import org.lwjgl.vulkan.VK10.vkDestroySemaphore
import org.lwjgl.vulkan.VkSemaphoreCreateInfo


class Semaphore (val vkSemaphore: Long): VKContextClosable
{

	override fun close (vkCtx: VKContext)
	{
		vkDestroySemaphore(vkCtx.vkDevice, vkSemaphore, null)
	}

	companion object
	{
		operator fun invoke (context: VKContext): Semaphore
		{
			MemoryStack.stackPush().use { stack ->
				val semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack).`sType$Default`()
				val lp = stack.mallocLong(1)
				vkCheck(
					vkCreateSemaphore(context.vkDevice, semaphoreCreateInfo, null, lp),
					"Failed to create semaphore"
				)
				return Semaphore(lp.get(0))
			}
		}
	}
}