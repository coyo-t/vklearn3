package fpw.ren.gpu

import org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT
import org.lwjgl.vulkan.VK10.VK_VERTEX_INPUT_RATE_VERTEX
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription


class GPUVertexBufferStruct
{

	val vi = VkPipelineVertexInputStateCreateInfo.calloc()
	val viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES)
	val viBindings = VkVertexInputBindingDescription.calloc(1)

	init
	{
		val i = 0
		val offset = 0
		// Position
		viAttrs.get(i)
			.binding(0)
			.location(i)
			.format(VK_FORMAT_R32G32B32_SFLOAT)
			.offset(offset)

		viBindings.get(0)
			.binding(0)
			.stride(POSITION_COMPONENTS * GPUtil.SIZEOF_FLOAT)
			.inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

		vi
			.`sType$Default`()
			.pVertexBindingDescriptions(viBindings)
			.pVertexAttributeDescriptions(viAttrs)
	}

	fun cleanup()
	{
		viBindings.free()
		viAttrs.free()
	}

	companion object
	{
		const val NUMBER_OF_ATTRIBUTES = 1
		const val POSITION_COMPONENTS = 3
	}

}