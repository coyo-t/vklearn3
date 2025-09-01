package fpw.ren.gpu

import fpw.Renderer
import org.lwjgl.vulkan.VK10.vkDestroyShaderModule


class ShaderModule (
	val handle: Long,
	val shaderStage: Int,
)
{
	fun free(context: Renderer)
	{
		vkDestroyShaderModule(context.vkDevice, handle, null)
	}
}