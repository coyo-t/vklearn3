package fpw.ren.gpu

import org.lwjgl.vulkan.EXTFilterCubic.VK_FILTER_CUBIC_EXT
import org.lwjgl.vulkan.VK10.*

@JvmInline
value class SamplerFilter(val vkEnum: Int)
{
	companion object
	{
		@JvmStatic val NEAREST = SamplerFilter(VK_FILTER_NEAREST)
		@JvmStatic val LINEAR = SamplerFilter(VK_FILTER_LINEAR)
//		@JvmStatic val CUBIC = SamplerFilter(VK_FILTER_CUBIC_EXT)
	}
}