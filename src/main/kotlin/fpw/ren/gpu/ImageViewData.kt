package fpw.ren.gpu

import org.lwjgl.vulkan.VK10

data class ImageViewData(
	val aspectMask: Int = 0,
	val baseArrayLayer: Int = 0,
	val format: Int = 0,
	val layerCount: Int = 1,
	val mipLevels: Int = 1,
	val viewType: Int = VK10.VK_IMAGE_VIEW_TYPE_2D,
)