package fpw.ren.gpu

import org.lwjgl.vulkan.VK10.VK_FORMAT_UNDEFINED
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo

class PipelineBuildInfo(
	val colorFormat: Int,
	val shaderModules: List<ShaderModule>,
	val vi: VkPipelineVertexInputStateCreateInfo,
	val depthFormat: Int = VK_FORMAT_UNDEFINED,
	val pushConstRange: List<PushConstantRange> = emptyList(),
)
