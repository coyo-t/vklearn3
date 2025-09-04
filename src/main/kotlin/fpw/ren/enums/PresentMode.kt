package fpw.ren.enums

import org.lwjgl.vulkan.KHRSurface

enum class PresentMode(val vk: Int)
{
	Immediate(KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR),
	Mailbox(KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR),
	FirstInFirstOut(KHRSurface.VK_PRESENT_MODE_FIFO_KHR),
}