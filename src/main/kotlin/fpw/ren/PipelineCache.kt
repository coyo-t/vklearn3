package fpw.ren

import fpw.Renderer
import org.lwjgl.vulkan.VK10.vkDestroyPipelineCache


class PipelineCache (
	val context: Renderer,
	val vkPipelineCache: Long,
)
{
	fun free ()
	{
		vkDestroyPipelineCache(context.vkDevice, vkPipelineCache, null)
	}
}