package fpw.ren.gpu

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo

class GPUPipeLineBuildInfo(
	val colorFormat: Int,
	val shaderModules: List<GPUShaderModule>,
	val vi: VkPipelineVertexInputStateCreateInfo,
)
