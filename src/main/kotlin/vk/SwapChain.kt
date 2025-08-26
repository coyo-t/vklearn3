package com.catsofwar.vk

import com.catsofwar.Window
import com.catsofwar.vk.ImageView.ImageViewData
import com.catsofwar.vk.Surface.SurfaceFormat
import com.catsofwar.vk.VKUtil.vkCheck
import org.joml.Math.clamp
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
import org.tinylog.kotlin.Logger


class SwapChain (
	window: Window,
	device: Device,
	surface: Surface,
	requestedImages: Int,
	vsync: Boolean,
)
{

	val imageViews: List<ImageView>
	val numImages: Int
	val swapChainExtent: VkExtent2D
	val vkSwapChain: Long

	init
	{
		Logger.debug("Creating Vulkan SwapChain")
		MemoryStack.stackPush().use { stack ->
			val surfaceCaps: VkSurfaceCapabilitiesKHR = surface.surfaceCaps
			val reqImages = calcNumImages(surfaceCaps, requestedImages)
			swapChainExtent = calcSwapChainExtent(window, surfaceCaps)

			val surfaceFormat: SurfaceFormat = surface.surfaceFormat
			val vkSwapchainCreateInfo = VkSwapchainCreateInfoKHR.calloc(stack)
				.`sType$Default`()
				.surface(surface.vkSurface)
				.minImageCount(reqImages)
				.imageFormat(surfaceFormat.imageFormat)
				.imageColorSpace(surfaceFormat.colorSpace)
				.imageExtent(swapChainExtent)
				.imageArrayLayers(1)
				.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
				.preTransform(surfaceCaps.currentTransform())
				.compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
				.clipped(true)
			if (vsync)
			{
				vkSwapchainCreateInfo.presentMode(KHRSurface.VK_PRESENT_MODE_FIFO_KHR)
			}
			else
			{
				vkSwapchainCreateInfo.presentMode(KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR)
			}

			val lp = stack.mallocLong(1)
			vkCheck(
				KHRSwapchain.vkCreateSwapchainKHR(device.vkDevice, vkSwapchainCreateInfo, null, lp),
				"Failed to create swap chain"
			)
			vkSwapChain = lp.get(0)

			imageViews = createImageViews(stack, device, vkSwapChain, surfaceFormat.imageFormat)
			numImages = imageViews.size
		}
	}

	private fun calcNumImages(surfCapabilities: VkSurfaceCapabilitiesKHR, requestedImages: Int): Int
	{
		val maxImages = surfCapabilities.maxImageCount()
		val minImages = surfCapabilities.minImageCount()
		var result = minImages
		if (maxImages != 0)
		{
			result = minOf(requestedImages, maxImages)
		}
		result = maxOf(result, minImages)
		Logger.debug(
			"Requested [{}] images, got [{}] images. Surface capabilities, maxImages: [{}], minImages [{}]",
			requestedImages, result, maxImages, minImages
		)

		return result
	}

	private fun calcSwapChainExtent(window: Window, surfCapabilities: VkSurfaceCapabilitiesKHR): VkExtent2D
	{
		val result = VkExtent2D.calloc()
		if (surfCapabilities.currentExtent().width() == -0x1)
		{
			// Surface size undefined. Set to the window size if within bounds
			val maxExts = surfCapabilities.maxImageExtent()
			val minExts = surfCapabilities.minImageExtent()

//			var width = minOf(window.wide, maxExts.width())
//			width = maxOf(width, minExts.width())
//			var height = minOf(window.tall, maxExts.height())
//			height = maxOf(height, minExts.height())

			result.width(clamp(window.wide, minExts.width(), maxExts.width()))
			result.height(clamp(window.tall, minExts.height(), maxExts.height()))
		}
		else
		{
			// Surface already defined, just use that for the swap chain
			result.set(surfCapabilities.currentExtent())
		}
		return result
	}

	private fun createImageViews(stack: MemoryStack, device: Device, swapChain: Long, format: Int): List<ImageView>
	{
		val ip = stack.mallocInt(1)
		vkCheck(
			KHRSwapchain.vkGetSwapchainImagesKHR(device.vkDevice, swapChain, ip, null),
			"Failed to get number of surface images"
		)
		val numImages = ip.get(0)

		val swapChainImages = stack.mallocLong(numImages)
		vkCheck(
			KHRSwapchain.vkGetSwapchainImagesKHR(device.vkDevice, swapChain, ip, swapChainImages),
			"Failed to get surface images"
		)

		val imageViewData = ImageViewData(
			format = format,
			aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
		)
		return (0..<numImages).map { ImageView(device, swapChainImages[it], imageViewData) }
	}

	fun cleanup(device: Device)
	{
		Logger.debug("Destroying Vulkan SwapChain")
		swapChainExtent.free()
		imageViews.forEach { it.cleanup(device) }
		KHRSwapchain.vkDestroySwapchainKHR(device.vkDevice, vkSwapChain, null)
	}

}