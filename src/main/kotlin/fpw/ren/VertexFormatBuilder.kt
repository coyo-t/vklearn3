package fpw.ren

import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription

/*
	UNORM is a float in the range of [0, 1].
	SNORM is the same but in the range of [-1, 1]
	USCALED is the unsigned integer value converted to float
	SSCALED is the integer value converted to float
	UINT is an unsigned integer
	SINT is a signed integer
*/

class VertexFormatBuilder
{
	companion object
	{
		fun buildVertexFormat (vb: VertexFormatBuilder.()->Unit): VertexFormat
		{
			with (VertexFormatBuilder())
			{
				vb.invoke(this)

				val attrCount = attributes.size
				val vip = VkVertexInputAttributeDescription.calloc(attrCount)
				var stride = 0
				for ((i, attr) in attributes.withIndex())
				{
					vip[i].apply {
						binding(0)
						location(i)
						format(attr.type.vk)
						offset(stride)
					}
					stride += attr.type.byteSize
				}
				val vbl = VkVertexInputBindingDescription.calloc(1)
				vbl[0].apply {
					binding(0)
					inputRate(VK_VERTEX_INPUT_RATE_VERTEX)
					stride(stride)
				}

				val vinfo = VkPipelineVertexInputStateCreateInfo.calloc()
				vinfo.apply {
					`sType$Default`()
					pVertexBindingDescriptions(vbl)
					pVertexAttributeDescriptions(vip)
				}
				return VertexFormat(
					viAttrs = vip,
					viBindings = vbl,
					vi=vinfo,
					stride=stride,
				)
			}
		}

		class AttributeType (
			val componentByteSize: Int,
			val componentCount: Int,
			val vk: Int,
		)
		{
			val byteSize = componentByteSize * componentCount
		}

		val UNDEFINED = AttributeType(0, 0, VK_FORMAT_UNDEFINED)

		val UBYTE1 = AttributeType(1, 1, VK_FORMAT_R8_UINT)
		val UBYTE2 = AttributeType(1, 2, VK_FORMAT_R8G8_UINT)
		val UBYTE3 = AttributeType(1, 3, VK_FORMAT_R8G8B8_UINT)
		val UBYTE4 = AttributeType(1, 4, VK_FORMAT_R8G8B8A8_UINT)

		val SF8_1 = AttributeType(1, 1, VK_FORMAT_R8_SNORM)
		val SF8_2 = AttributeType(1, 2, VK_FORMAT_R8G8_SNORM)
		val SF8_3 = AttributeType(1, 3, VK_FORMAT_R8G8B8_SNORM)
		val SF8_4 = AttributeType(1, 4, VK_FORMAT_R8G8B8A8_SNORM)

		val UF8_1 = AttributeType(1, 1, VK_FORMAT_R8_UNORM)
		val UF8_2 = AttributeType(1, 2, VK_FORMAT_R8G8_UNORM)
		val UF8_3 = AttributeType(1, 3, VK_FORMAT_R8G8B8_UNORM)
		val UF8_4 = AttributeType(1, 4, VK_FORMAT_R8G8B8A8_UNORM)

		val FLOAT1 = AttributeType(4, 1, VK_FORMAT_R32_SFLOAT)
		val FLOAT2 = AttributeType(4, 2, VK_FORMAT_R32G32_SFLOAT)
		val FLOAT3 = AttributeType(4, 3, VK_FORMAT_R32G32B32_SFLOAT)
		val FLOAT4 = AttributeType(4, 4, VK_FORMAT_R32G32B32A32_SFLOAT)
	}

	class Entry (
		var type: AttributeType,
	)

	val attributes = mutableListOf<Entry>()

	fun custom (format: AttributeType): Entry
	{
		val outs = Entry(
			type=format,
		)
		attributes += outs
		return outs
	}

	fun scalar () = custom(
		format=FLOAT1,
	)

	fun location3D () = custom(
		format=FLOAT3,
	)

	fun texcoord2D () = custom(
		format=FLOAT2,
	)

	fun normal () = custom(
		format=FLOAT3,
	)

	fun tint8 () = custom(
		format=UF8_4,
	)

}