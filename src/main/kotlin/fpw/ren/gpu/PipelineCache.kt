package fpw.ren.gpu

import org.lwjgl.vulkan.VK10.vkDestroyPipelineCache


class PipelineCache (val vkPipelineCache: Long)
{
	fun free (context: GPUContext)
	{
		vkDestroyPipelineCache(context.vkDevice, vkPipelineCache, null)
	}
}