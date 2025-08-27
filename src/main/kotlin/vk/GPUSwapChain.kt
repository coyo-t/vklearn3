package com.catsofwar.vk

import com.catsofwar.Main
import com.catsofwar.Window
import com.catsofwar.vk.GPUImageView.ImageViewData
import com.catsofwar.vk.GPUtil.vkCheck
import org.joml.Math.clamp
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR
import org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR
import org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR
import org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR
import org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR
import org.lwjgl.vulkan.VK14.*
import kotlin.math.max
import kotlin.math.min


class GPUSwapChain (
	window: Window,
	device: GPUDevice,
	surface: GPUSurface,
	requestedImages: Int,
	vsync: Boolean,
)
{

	val imageViews: List<GPUImageView>
	val numImages: Int
	val swapChainExtent: VkExtent2D
	val vkSwapChain: Long

	init
	{
		MemoryStack.stackPush().use { stack ->
			Main.logDebug("Creating Vulkan SwapChain")
			val surfaceCaps = surface.surfaceCaps
			val reqImages = calcNumImages(surfaceCaps, requestedImages)
			swapChainExtent = calcSwapChainExtent(window, surfaceCaps)

			val surfaceFormat = surface.surfaceFormat
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
				.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
				.clipped(true)

			vkSwapchainCreateInfo.presentMode(
				if (vsync) VK_PRESENT_MODE_FIFO_KHR
				else VK_PRESENT_MODE_IMMEDIATE_KHR
			)

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
			result = min(requestedImages, maxImages)
		}
		result = max(result, minImages)
		Main.logDebug(
			"Requested [$requestedImages] images, got [$result] images. " +
			"Surface capabilities, " +
			"image range ($minImages..$maxImages)"
		)

		return result
	}

	private fun calcSwapChainExtent(window: Window, surfCapabilities: VkSurfaceCapabilitiesKHR): VkExtent2D
	{
		val result = VkExtent2D.calloc()
		if (surfCapabilities.currentExtent().width() == -0x1)
		{
			// Surface size undefined. Set to the window size if within bounds
			val minn = surfCapabilities.minImageExtent()
			val maxx = surfCapabilities.maxImageExtent()
			result.width(clamp(window.wide, minn.width(), maxx.width()))
			result.height(clamp(window.tall, minn.height(), maxx.height()))
		}
		else
		{
			// Surface already defined, just use that for the swap chain
			result.set(surfCapabilities.currentExtent())
		}
		return result
	}

	private fun createImageViews(stack: MemoryStack, device: GPUDevice, swapChain: Long, format: Int): List<GPUImageView>
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
		return List(numImages) {
			GPUImageView(device, swapChainImages[it], imageViewData)
		}
	}

	fun acquireNextImage(device: GPUDevice, imageAqSem: GPUSemaphore): Int
	{
		val imageIndex: Int
		MemoryStack.stackPush().use { stack ->
			val ip = stack.mallocInt(1)
			val err = KHRSwapchain.vkAcquireNextImageKHR(
				device.vkDevice, vkSwapChain, 0L.inv(),
				imageAqSem.vkSemaphore, MemoryUtil.NULL, ip
			)
			if (err == VK_ERROR_OUT_OF_DATE_KHR)
			{
				return -1
			}
			else if (err == VK_SUBOPTIMAL_KHR)
			{
				// Not optimal but swapchain can still be used
			}
			else if (err != VK_SUCCESS)
			{
				throw RuntimeException("Failed to acquire image: $err")
			}
			imageIndex = ip.get(0)
		}
		return imageIndex
	}

	fun cleanup(device: GPUDevice)
	{
		Main.logDebug("Destroying Vulkan SwapChain")
		swapChainExtent.free()
		for (it in imageViews)
		{
			it.cleanup(device)
		}
		KHRSwapchain.vkDestroySwapchainKHR(device.vkDevice, vkSwapChain, null)
	}


	fun presentImage(queue: GPUCommandQueue, renderCompleteSem: GPUSemaphore, imageIndex: Int): Boolean
	{
		var resize = false
		MemoryStack.stackPush().use { stack ->
			val present = VkPresentInfoKHR.calloc(stack)
				.`sType$Default`()
				.pWaitSemaphores(stack.longs(renderCompleteSem.vkSemaphore))
				.swapchainCount(1)
				.pSwapchains(stack.longs(vkSwapChain))
				.pImageIndices(stack.ints(imageIndex))
			val err = KHRSwapchain.vkQueuePresentKHR(queue.vkQueue, present)
			if (err == VK_ERROR_OUT_OF_DATE_KHR)
			{
				resize = true
			}
			else if (err == VK_SUBOPTIMAL_KHR)
			{
				// Not optimal but swap chain can still be used
			}
			else if (err != VK_SUCCESS)
			{
				throw RuntimeException("Failed to present KHR: $err")
			}
		}
		return resize
	}

}