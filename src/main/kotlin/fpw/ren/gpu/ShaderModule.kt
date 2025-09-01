package fpw.ren.gpu

import org.lwjgl.vulkan.VK10.vkDestroyShaderModule


class ShaderModule (
	val handle: Long,
	val shaderStage: Int,
)
{
	fun free(context: GPUContext)
	{
		vkDestroyShaderModule(context.vkDevice, handle, null)
	}
}