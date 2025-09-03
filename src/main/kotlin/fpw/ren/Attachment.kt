package fpw.ren

import fpw.Renderer
import org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT
import org.lwjgl.vulkan.VK14.*



class Attachment (
	val context: Renderer,
	width: Int,
	height: Int,
	format: Int,
	usage: Int,
)
{
	val image: GPUImage
	val imageView: ImageView
	var isDepthAttachment = false
		internal set

	init
	{
		val imageData = GPUImage.Data(
			wide = width,
			tall = height,
			usage = usage or VK_IMAGE_USAGE_SAMPLED_BIT,
			format = format,
			memUsage = VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT,
		)
		image = GPUImage(context, imageData)

		var aspectMask = 0

		atMapping.forEach { (mask, callback) ->
			if ((usage and mask) != 0)
			{
				aspectMask = callback.invoke(this)
			}
		}

		val imageViewData = ImageView.Data(
			format = image.format,
			aspectMask = aspectMask,

		)
		imageView = ImageView(
			context.gpu,
			image.vkImage,
			imageViewData,
			isDepthImage = isDepthAttachment,
		)
	}
	fun free ()

	{
		imageView.free()
		image.free()
	}

	companion object
	{
		val atMapping = listOf<Pair<Int, (Attachment.()->Int)>>(
			VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT to {
				VK_IMAGE_ASPECT_COLOR_BIT
			},
			VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT to {
				isDepthAttachment = true
				VK_IMAGE_ASPECT_DEPTH_BIT
			}
		)
	}
}