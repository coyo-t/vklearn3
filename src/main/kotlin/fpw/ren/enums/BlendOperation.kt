package fpw.ren.enums

import org.lwjgl.vulkan.VK10

enum class BlendOperation(val vk: Int)
{
	Add(VK10.VK_BLEND_OP_ADD),
	Subtract(VK10.VK_BLEND_OP_SUBTRACT),
	InverseSubtract(VK10.VK_BLEND_OP_REVERSE_SUBTRACT),
	Min(VK10.VK_BLEND_OP_MIN),
	Max(VK10.VK_BLEND_OP_MAX),
}