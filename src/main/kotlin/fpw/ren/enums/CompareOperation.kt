package fpw.ren.enums

import org.lwjgl.vulkan.VK10

enum class CompareOperation(val vk:Int)
{
	LessThan(VK10.VK_COMPARE_OP_LESS),
	LessThanOrEqual(VK10.VK_COMPARE_OP_LESS_OR_EQUAL),
	GreaterThan(VK10.VK_COMPARE_OP_GREATER),
	GreaterThanOrEqual(VK10.VK_COMPARE_OP_GREATER_OR_EQUAL),

	Never(VK10.VK_COMPARE_OP_NEVER),
	Equal(VK10.VK_COMPARE_OP_EQUAL),
	Different(VK10.VK_COMPARE_OP_NOT_EQUAL),
	Always(VK10.VK_COMPARE_OP_ALWAYS),
}