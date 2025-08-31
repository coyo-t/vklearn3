package fpw.ren.gpu

import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription


class GPUVertexBufferStruct: AutoCloseable
{
	companion object
	{
		const val NUMBER_OF_ATTRIBUTES = 2
		const val POSITION_COMPONENTS = 3
		const val TEXCO_COMPONENTS = 2

		class Attribute(
			val location:Int,
			val format:Int,
			val binding:Int=0,
		)
	}

	val vi = VkPipelineVertexInputStateCreateInfo.calloc()
	val viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES)
	val viBindings = VkVertexInputBindingDescription.calloc(1)

	init
	{
		var i = 0
		var offset = 0

		// Position
		viAttrs
		.get(i)
		.binding(0)
		.location(i)
		.format(VK_FORMAT_R32G32B32_SFLOAT)
		.offset(offset)

		offset += POSITION_COMPONENTS * GPUtil.SIZEOF_FLOAT
		i += 1

		// Texture coordinates
		viAttrs
		.get(i)
		.binding(0)
		.location(i)
		.format(VK_FORMAT_R32G32_SFLOAT)
		.offset(offset)

		offset += TEXCO_COMPONENTS * GPUtil.SIZEOF_FLOAT
		i += 1

		val stride = offset
		viBindings
		.get(0)
		.binding(0)
		.stride(stride)
		.inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

		vi
		.`sType$Default`()
		.pVertexBindingDescriptions(viBindings)
		.pVertexAttributeDescriptions(viAttrs)
	}

	override fun close ()
	{
		viBindings.free()
		viAttrs.free()
		vi.free()
	}

}