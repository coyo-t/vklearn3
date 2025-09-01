package fpw.ren.gpu

import fpw.Renderer
import org.lwjgl.vulkan.VK10.vkDestroyPipelineCache

@JvmInline
value class PipelineCache (val vkPipelineCache: Long)
{
	fun free (context: Renderer)
	{
		vkDestroyPipelineCache(context.vkDevice, vkPipelineCache, null)
	}
}