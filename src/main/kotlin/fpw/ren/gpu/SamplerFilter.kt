package fpw.ren.gpu

import org.lwjgl.vulkan.EXTFilterCubic.VK_FILTER_CUBIC_EXT
import org.lwjgl.vulkan.VK10.*

@JvmInline
value class SamplerFilter(val vkEnum: Int)
{
	companion object
	{
		val NEAREST = SamplerFilter(VK_FILTER_NEAREST)
		val LINEAR = SamplerFilter(VK_FILTER_LINEAR)
//		val CUBIC = SamplerFilter(VK_FILTER_CUBIC_EXT)
	}
}