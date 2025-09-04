package fpw.ren.enums

import org.lwjgl.vulkan.VK10

enum class WindingOrder (val vk: Int)
{
	Clockwise(VK10.VK_FRONT_FACE_CLOCKWISE),
	CounterClockwise(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE),
}