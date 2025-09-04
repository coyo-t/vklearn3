package fpw.ren.enums

import org.lwjgl.vulkan.VK10

enum class CullingMode (val vk: Int)
{
	None(VK10.VK_CULL_MODE_NONE),
	Front(VK10.VK_CULL_MODE_FRONT_BIT),
	Back(VK10.VK_CULL_MODE_BACK_BIT),
	Both(VK10.VK_CULL_MODE_FRONT_AND_BACK),
}