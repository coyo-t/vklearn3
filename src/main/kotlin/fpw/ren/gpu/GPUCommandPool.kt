package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandPoolCreateInfo

class GPUCommandPool
private constructor (val vkCommandPool: Long)
{
	companion object
	{
		operator fun invoke (vkCtx: GPUContext, queueFamilyIndex: Int, supportReset: Boolean): GPUCommandPool
		{
			MemoryStack.stackPush().use { stack ->
//				Main.logDebug("Creating Vulkan command pool")
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
				return GPUCommandPool(lp[0])
			}
		}
	}

	fun cleanup(vkCtx: GPUContext)
	{
//		Main.logDebug("Destroying Vulkan command pool")
		vkDestroyCommandPool(vkCtx.device.vkDevice, vkCommandPool, null)
	}

	fun reset(vkCtx: GPUContext)
	{
		vkResetCommandPool(vkCtx.device.vkDevice, vkCommandPool, 0)
	}

}