package fpw.ren.texture

import org.lwjgl.vulkan.VK10

enum class SamplerFilter(val vkEnum: Int)
{
	Nearest(VK10.VK_FILTER_NEAREST),
	Linear(VK10.VK_FILTER_LINEAR),
}