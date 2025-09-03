package fpw.ren

import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.VK10

enum class ShaderType (
	val vkFlag: Int,
	val scEnum: Int,
)
{
	Vertex(
		VK10.VK_SHADER_STAGE_VERTEX_BIT,
		Shaderc.shaderc_vertex_shader,
	),
	Fragment(
		VK10.VK_SHADER_STAGE_FRAGMENT_BIT,
		Shaderc.shaderc_fragment_shader,
	),
	Compute(
		VK10.VK_SHADER_STAGE_COMPUTE_BIT,
		Shaderc.shaderc_compute_shader,
	),
}