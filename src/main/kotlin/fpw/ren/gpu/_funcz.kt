package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandPoolCreateInfo
import org.lwjgl.vulkan.VkFenceCreateInfo
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo
import org.lwjgl.vulkan.VkSemaphoreCreateInfo


fun GPUContext.createFence (signaled: Boolean): GPUFence
{
	MemoryStack.stackPush().use { stack ->
		val fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
			.`sType$Default`()
			.flags(if (signaled) VK_FENCE_CREATE_SIGNALED_BIT else 0)
		val lp = stack.mallocLong(1)
		vkCheck(vkCreateFence(vkDevice, fenceCreateInfo, null, lp), "Failed to create fence")
		return GPUFence(lp[0])
	}
}

fun GPUContext.createSemaphor(): Semaphore
{
	MemoryStack.stackPush().use { stack ->
		val semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack).`sType$Default`()
		val lp = stack.mallocLong(1)
		vkCheck(
			vkCreateSemaphore(vkDevice, semaphoreCreateInfo, null, lp),
			"Failed to create semaphore"
		)
		return Semaphore(lp.get(0))
	}
}


fun GPUContext.createCommandPool (
	queueFamilyIndex: Int,
	supportReset: Boolean,
): CommandPool
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
		vkCheck(
			vkCreateCommandPool(device.vkDevice, cmdPoolInfo, null, lp),
			"Failed to create command pool"
		)
		return CommandPool(lp[0])
	}
}

fun LogicalDevice.createPipelineCache(): PipelineCache
{
	val outs = MemoryStack.stackPush().use { stack ->
		val createInfo = VkPipelineCacheCreateInfo.calloc(stack).`sType$Default`()
		val lp = stack.mallocLong(1)
		vkCheck(
			vkCreatePipelineCache(vkDevice, createInfo, null, lp),
			"Error creating pipeline cache"
		)
		lp.get(0)
	}
	return PipelineCache(outs)
}
