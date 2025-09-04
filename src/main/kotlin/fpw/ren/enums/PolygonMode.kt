package fpw.ren.enums

import org.lwjgl.vulkan.VK10.*

enum class PolygonMode(val vk: Int)
{
	Filled(VK_POLYGON_MODE_FILL),
	Lines(VK_POLYGON_MODE_LINE),
	Points(VK_POLYGON_MODE_POINT),
}


