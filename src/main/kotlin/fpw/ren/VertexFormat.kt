package fpw.ren

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
	fun free ()
	{
		viBindings.free()
		viAttrs.free()
		vi.free()
	}
}