package fpw.ren.gpu

import fpw.Renderer
import org.lwjgl.vulkan.VK14.*



class Attachment
{
	val image: GPUImage
	val imageView: ImageView
	var isDepthAttachment = false
		internal set

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

	constructor (vkCtx: Renderer, width: Int, height: Int, format: Int, usage: Int)
	{
		val imageData = GPUImage.Data(
			wide = width,
			tall = height,
			usage = usage or VK_IMAGE_USAGE_SAMPLED_BIT,
			format = format,
		)
		image = GPUImage(vkCtx, imageData)

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
			vkCtx.device,
			image.vkImage,
			imageViewData,
			isDepthImage = isDepthAttachment,
		)
	}

	fun close (context: Renderer)
	{
		imageView.free(context.device)
		image.free(context)
	}

}