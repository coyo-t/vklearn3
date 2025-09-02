package fpw.ren.gpu

import org.lwjgl.vulkan.VK10.*

@JvmInline
value class SamplerWrapping(val vkEnum: Int)
{

	companion object
	{
		@JvmStatic val REPEAT = SamplerWrapping(VK_SAMPLER_ADDRESS_MODE_REPEAT)
		@JvmStatic val EXTEND = SamplerWrapping(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
		@JvmStatic val CLIP = SamplerWrapping(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER)
		@JvmStatic val MIRROR = SamplerWrapping(VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT)
	}
}