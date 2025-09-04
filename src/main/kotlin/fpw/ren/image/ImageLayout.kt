package fpw.ren.image

import org.lwjgl.vulkan.VK14.*

enum class ImageLayout(val vk: Int)
{
	Undefined(
		VK_IMAGE_LAYOUT_UNDEFINED
	),
	General(
		VK_IMAGE_LAYOUT_GENERAL
	),
	ColorAttachmentOptimal(
		VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
	),
	OptimalDepthStencilAttachment(
		VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
	),
	OptimalDepthStencilReadOnly(
		VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
	),
	OptimalShaderReadOnly(
		VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
	),
	OptimalTransferSrc(
		VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL
	),
	OptimalTransferDst(
		VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
	),
	PreInitialized(
		VK_IMAGE_LAYOUT_PREINITIALIZED
	),

}