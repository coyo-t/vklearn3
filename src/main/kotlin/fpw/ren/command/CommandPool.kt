package fpw.ren.command

import fpw.ren.Renderer
import fpw.ren.GPUtil
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkCommandPoolCreateInfo

class CommandPool (
	val vkCtx: Renderer,
	queueFamilyIndex: Int,
	supportReset: Boolean,
)
{
	val vkCommandPool: Long

	init
	{
		MemoryStack.stackPush().use { stack ->
			val cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
				.`sType$Default`()
				.queueFamilyIndex(queueFamilyIndex)
			if (supportReset)
			{
				cmdPoolInfo.flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
			}

			val lp = stack.mallocLong(1)
			GPUtil.gpuCheck(
				VK10.vkCreateCommandPool(vkCtx.gpu.logicalDevice.vkDevice, cmdPoolInfo, null, lp),
				"Failed to create command pool"
			)
			vkCommandPool = lp[0]
		}
	}

	fun reset ()
	{
		VK10.vkResetCommandPool(vkCtx.gpu.logicalDevice.vkDevice, vkCommandPool, 0)
	}

	fun free ()
	{
		VK10.vkDestroyCommandPool(vkCtx.gpu.logicalDevice.vkDevice, vkCommandPool, null)
	}
}