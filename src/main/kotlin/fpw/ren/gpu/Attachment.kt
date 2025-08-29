package fpw.ren.gpu

import org.lwjgl.vulkan.VK14.*



class Attachment: GPUClosable
{
	val image: GPUImage
	val imageView: ImageView
	val isDepthAttachment: Boolean

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
		var dpm = false
		if ((usage and VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) != 0)
		{
			aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
		}
		if ((usage and VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0)
		{
			aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT
			dpm = true
		}
		isDepthAttachment = dpm

		val imageViewData = ImageViewData(
			format = image.format,
			aspectMask = aspectMask,
		)
		imageView = ImageView(vkCtx.device, image.vkImage, imageViewData)
	}

	override fun close (context: GPUContext)
	{
		imageView.close(context.device)
		image.close(context)
	}

}