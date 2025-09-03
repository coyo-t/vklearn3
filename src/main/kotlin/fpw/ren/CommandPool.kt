package fpw.ren

import fpw.Renderer
import fpw.ren.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandPoolCreateInfo
import kotlin.use

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
				cmdPoolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
			}

			val lp = stack.mallocLong(1)
			gpuCheck(
				vkCreateCommandPool(vkCtx.device.vkDevice, cmdPoolInfo, null, lp),
				"Failed to create command pool"
			)
			vkCommandPool = lp[0]
		}
	}

	fun reset ()
	{
		vkResetCommandPool(vkCtx.device.vkDevice, vkCommandPool, 0)
	}

	fun free ()
	{
		vkDestroyCommandPool(vkCtx.device.vkDevice, vkCommandPool, null)
	}
}