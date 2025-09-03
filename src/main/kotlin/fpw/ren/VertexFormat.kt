package fpw.ren

import fpw.FUtil
import org.lwjgl.system.MemoryUtil.nmemFree
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription


class VertexFormat (
	val vi: VkPipelineVertexInputStateCreateInfo,
	val viAttrs: VkVertexInputAttributeDescription.Buffer,
	val viBindings: VkVertexInputBindingDescription.Buffer,
	val stride: Int,
)
{
	init
	{
		GPUtil.registerPointersForCleanup(
			this,
			vi,
			viAttrs,
			viBindings,
		)
	}
}