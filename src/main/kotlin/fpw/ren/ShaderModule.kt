package fpw.ren

import fpw.Renderer
import org.lwjgl.vulkan.VK10.vkDestroyShaderModule


class ShaderModule (
	val renderer: Renderer,
	val handle: Long,
	val shaderStage: Int,
)
{
	fun free()
	{
		vkDestroyShaderModule(renderer.gpu.logicalDevice.vkDevice, handle, null)
	}
}