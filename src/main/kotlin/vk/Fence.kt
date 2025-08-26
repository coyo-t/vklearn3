package com.catsofwar.vk

import com.catsofwar.vk.VKUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkFenceCreateInfo

@JvmInline
value class Fence private constructor (val vkFence: Long)
{
	companion object
	{
		operator fun invoke (vkCtx: VKContext, signaled: Boolean): Fence
		{
			MemoryStack.stackPush().use { stack ->
				val fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
					.`sType$Default`()
					.flags(if (signaled) VK_FENCE_CREATE_SIGNALED_BIT else 0)
				val lp = stack.mallocLong(1)
				vkCheck(vkCreateFence(vkCtx.vkDevice, fenceCreateInfo, null, lp), "Failed to create fence")
				return Fence(lp.get(0))
			}
		}
	}

	fun cleanup(vkCtx: VKContext)
	{
		vkDestroyFence(vkCtx.device.vkDevice, vkFence, null)
	}

	fun fenceWait(vkCtx: VKContext)
	{
		vkWaitForFences(vkCtx.device.vkDevice, vkFence, true, Long.MAX_VALUE)
	}

	fun reset(vkCtx: VKContext)
	{
		vkResetFences(vkCtx.device.vkDevice, vkFence)
	}

}
