package fpw.ren

import org.lwjgl.vulkan.VK10.*

enum class SamplerFilter(val vkEnum: Int)
{
	NEAREST(VK_FILTER_NEAREST),
	LINEAR(VK_FILTER_LINEAR),
}