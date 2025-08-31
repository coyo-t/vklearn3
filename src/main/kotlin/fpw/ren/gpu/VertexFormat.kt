package fpw.ren.gpu

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription


class VertexFormat (
	val vi: VkPipelineVertexInputStateCreateInfo,
	val viAttrs: VkVertexInputAttributeDescription.Buffer,
	val viBindings: VkVertexInputBindingDescription.Buffer,
)
{
//	companion object
//	{
//		val cleaner = Cleaner.create()
//	}
//
//	init
//	{
//		val v1 = vi.address()
//		val v2 = viAttrs.address()
//		val v3 = viBindings.address()
//		cleaner.register(this) {
//			Main.logInfo("DESTROYZOR :3")
//			nmemFree(v1)
//			nmemFree(v2)
//			nmemFree(v3)
//		}
//	}

	fun close ()
	{
		viBindings.free()
		viAttrs.free()
		vi.free()
	}

}