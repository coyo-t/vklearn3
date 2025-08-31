package fpw.ren.gpu

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription


class GPUVertexBufferStruct (
	val vi: VkPipelineVertexInputStateCreateInfo,
	val viAttrs: VkVertexInputAttributeDescription.Buffer,
	val viBindings: VkVertexInputBindingDescription.Buffer,
): AutoCloseable
{
	override fun close ()
	{
		viBindings.free()
		viAttrs.free()
		vi.free()
	}

}