package fpw.ren.texture

import org.lwjgl.vulkan.VK10

enum class SamplerWrapping (val vkEnum: Int)
{
	Repeat(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT),
	Extend(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE),
	Clip(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER),
	Mirror(VK10.VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT),
}