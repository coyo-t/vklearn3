package fpw.ren

import fpw.ren.enums.Pr
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo


fun VkPipelineInputAssemblyStateCreateInfo.topology (pr: Pr)
	= topology(pr.vkEnum)
