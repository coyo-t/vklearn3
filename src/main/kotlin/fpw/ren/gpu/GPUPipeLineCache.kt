package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.vkCreatePipelineCache
import org.lwjgl.vulkan.VK10.vkDestroyPipelineCache
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo


class GPUPipeLineCache
private constructor (val vkPipelineCache: Long): GPUClosable
{
	override fun close(context: GPUContext)
	{
		vkDestroyPipelineCache(context.vkDevice, vkPipelineCache, null)
	}


	companion object
	{
		operator fun invoke (device: GPUDevice): GPUPipeLineCache
		{
			val outs = MemoryStack.stackPush().use { stack ->
				val createInfo = VkPipelineCacheCreateInfo.calloc(stack).`sType$Default`()
				val lp = stack.mallocLong(1)
				vkCheck(
					vkCreatePipelineCache(device.vkDevice, createInfo, null, lp),
					"Error creating pipeline cache"
				)
				lp.get(0)
			}
			return GPUPipeLineCache(outs)
		}
	}
}