package fpw.ren.gpu

import fpw.Renderer
import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer


fun Renderer.createFence (signaled: Boolean): GPUFence
{
	MemoryStack.stackPush().use { stack ->
		val fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
			.`sType$Default`()
			.flags(if (signaled) VK_FENCE_CREATE_SIGNALED_BIT else 0)
		val lp = stack.mallocLong(1)
		gpuCheck(vkCreateFence(vkDevice, fenceCreateInfo, null, lp), "Failed to create fence")
		return GPUFence(lp[0])
	}
}

fun Renderer.createSemaphor(): Semaphore
{
	MemoryStack.stackPush().use { stack ->
		val semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack).`sType$Default`()
		val lp = stack.mallocLong(1)
		gpuCheck(
			vkCreateSemaphore(vkDevice, semaphoreCreateInfo, null, lp),
			"Failed to create semaphore"
		)
		return Semaphore(lp.get(0))
	}
}


fun Renderer.createCommandPool (
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
		gpuCheck(
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
		gpuCheck(
			vkCreatePipelineCache(vkDevice, createInfo, null, lp),
			"Error creating pipeline cache"
		)
		lp.get(0)
	}
	return PipelineCache(outs)
}

fun Renderer.createShaderModule (shaderStage: Int, spirv: ByteBuffer): ShaderModule
{
	check(spirv.isDirect) {
		"requires direct buffer"
	}
	return ShaderModule(
		handle = this._createShaderModule(spirv),
		shaderStage = shaderStage,
	)
}

private fun Renderer._createShaderModule(spirv: ByteBuffer): Long
{
	MemoryStack.stackPush().use { stack ->
//				val pCode = stack.malloc(code.size).put(0, code)
		val pCode = spirv
		val moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack)
			.`sType$Default`()
			.pCode(pCode)

		val lp = stack.mallocLong(1)
		gpuCheck(
			vkCreateShaderModule(vkDevice, moduleCreateInfo, null, lp),
			"Failed to create shader module"
		)
		return lp.get(0)
	}
}

internal fun Renderer.getGraphicsQueueFamilyIndex(): Int
{
	val queuePropsBuff = hardware.vkQueueFamilyProps
	val uhh = queuePropsBuff.indexOfFirst { (it.queueFlags() and VK10.VK_QUEUE_GRAPHICS_BIT) != 0 }
	require(uhh >= 0) {
		"Failed to get graphics Queue family index"
	}
	return uhh
}

internal fun Renderer.getPresentQueueFamilyIndex(): Int
{
	MemoryStack.stackPush().use { stack ->
		val queuePropsBuff = hardware.vkQueueFamilyProps
		val numQueuesFamilies: Int = queuePropsBuff.capacity()
		val intBuff = stack.mallocInt(1)
		for (i in 0..<numQueuesFamilies)
		{
			KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(
				hardware.vkPhysicalDevice,
				i, displaySurface.vkSurface, intBuff
			)
			if (intBuff.get(0) == VK_TRUE)
			{
				return i
			}
		}
	}
	throw RuntimeException("Failed to get Presentation Queue family index")
}
