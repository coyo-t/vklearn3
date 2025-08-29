package fpw.ren.gpu

import org.lwjgl.vulkan.VK10.VK_FORMAT_UNDEFINED
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo

class GPUPipeLineBuildInfo(
	val colorFormat: Int,
	val shaderModules: List<GPUShaderModule>,
	val vi: VkPipelineVertexInputStateCreateInfo,
	val depthFormat: Int = VK_FORMAT_UNDEFINED,
	val pushConstRange: List<PushConstantRange> = emptyList(),
)
{

}
