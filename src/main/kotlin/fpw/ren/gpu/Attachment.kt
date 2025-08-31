package fpw.ren.gpu

import org.lwjgl.vulkan.VK14.*



class Attachment: GPUClosable
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

	constructor (vkCtx: GPUContext, width: Int, height: Int, format: Int, usage: Int)
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

//		var dpm = false
//		if ((usage and VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) != 0)
//		{
//			aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
//		}
//		if ((usage and VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0)
//		{
//			aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT
//			dpm = true
//		}
//		isDepthAttachment = dpm

		val imageViewData = ImageViewData(
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

	override fun close (context: GPUContext)
	{
		imageView.close(context.device)
		image.close(context)
	}

}