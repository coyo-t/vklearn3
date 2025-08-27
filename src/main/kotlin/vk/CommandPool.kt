package com.catsofwar.vk

import com.catsofwar.Main
import com.catsofwar.vk.VKUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandPoolCreateInfo


class CommandPool(vkCtx: VKContext, queueFamilyIndex: Int, supportReset: Boolean)
{
	val vkCommandPool: Long


	init
	{

		MemoryStack.stackPush().use { stack ->
			Main.logDebug("Creating Vulkan command pool")
			val cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
				.`sType$Default`()
				.queueFamilyIndex(queueFamilyIndex)
			if (supportReset)
			{
				cmdPoolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
			}

			val lp = stack.mallocLong(1)
			vkCheck(
				vkCreateCommandPool(vkCtx.device.vkDevice, cmdPoolInfo, null, lp),
				"Failed to create command pool"
			)
			vkCommandPool = lp.get(0)
		}
	}

	fun cleanup(vkCtx: VKContext)
	{
		Main.logDebug("Destroying Vulkan command pool")
		vkDestroyCommandPool(vkCtx.device.vkDevice, vkCommandPool, null)
	}

	fun reset(vkCtx: VKContext)
	{
		vkResetCommandPool(vkCtx.device.vkDevice, vkCommandPool, 0)
	}

}