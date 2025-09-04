package fpw.ren

import fpw.ren.GPUtil.gpuCheck
import fpw.ren.image.ImageView.Data
import fpw.ren.command.CommandSequence
import fpw.ren.device.GPUDevice
import fpw.ren.enums.PresentMode
import fpw.ren.image.ImageView
import org.joml.Math.clamp
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.KHRSurface.*
import org.lwjgl.vulkan.KHRSwapchain.*
import org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT
import org.lwjgl.vulkan.VK14.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
import org.lwjgl.vulkan.VK14.VK_SUCCESS
import org.lwjgl.vulkan.VkExtent2D
import org.lwjgl.vulkan.VkPresentInfoKHR
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR
import kotlin.math.max
import kotlin.math.min


class SwapChain (
	val renderer: Renderer,
	val device: GPUDevice,
	var wide: Int,
	var tall: Int,
	displaySurface: DisplaySurface,
	requestedImages: Int,
	vsync: Boolean,
)
{
	val extents: VkExtent2D
	val vkSwapChain: Long
	val renderThinger: List<FrameDataz>

	init
	{
		MemoryStack.stackPush().use { stack ->
			val surfaceCaps = displaySurface.surfaceCaps
			val reqImages = run {
				val maxImages = surfaceCaps.maxImageCount()
				val minImages = surfaceCaps.minImageCount()
				var result = minImages
				if (maxImages != 0)
				{
					result = min(requestedImages, maxImages)
				}
				max(result, minImages)
			}
			extents = GPUtil.registerPointerForCleanup(VkExtent2D.calloc())
			if (surfaceCaps.currentExtent().width() == -0x1)
			{
				// Surface size undefined. Set to the window size if within bounds
				val minn = surfaceCaps.minImageExtent()
				val maxx = surfaceCaps.maxImageExtent()
				extents.width(clamp(wide, minn.width(), maxx.width()))
				extents.height(clamp(tall, minn.height(), maxx.height()))
			}
			else
			{
				// Surface already defined, just use that for the swap chain
				extents.set(surfaceCaps.currentExtent())
			}

			val surfaceFormat = displaySurface.surfaceFormat
			val createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
			createInfo.`sType$Default`()
			createInfo.surface(displaySurface.vkSurface)
			createInfo.minImageCount(reqImages)
			createInfo.imageFormat(surfaceFormat.imageFormat)
			createInfo.imageColorSpace(surfaceFormat.colorSpace)
			createInfo.imageExtent(extents)
			createInfo.imageArrayLayers(1)
			createInfo.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
			createInfo.preTransform(surfaceCaps.currentTransform())
			createInfo.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
			createInfo.clipped(true)

			createInfo.presentMode(
				if (vsync) PresentMode.FirstInFirstOut.vk
				else PresentMode.Immediate.vk
			)

			val lp = stack.mallocLong(1)
			gpuCheck(
				vkCreateSwapchainKHR(device.logicalDevice.vkDevice, createInfo, null, lp),
				"Failed to create swap chain"
			)
			vkSwapChain = lp.get(0)

			val ip = stack.mallocInt(1)
			gpuCheck(
				vkGetSwapchainImagesKHR(device.logicalDevice.vkDevice, vkSwapChain, ip, null),
				"failed to get number of surface images"
			)
			val numImages = ip.get(0)
			val swapChainImages = stack.mallocLong(numImages)
			gpuCheck(
				vkGetSwapchainImagesKHR(device.logicalDevice.vkDevice, vkSwapChain, ip, swapChainImages),
				"failed to get surface images"
			)
			val imageViewData = Data(
				format = surfaceFormat.imageFormat,
				aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
			)
			renderThinger = List(numImages) {
				val im = ImageView(device, swapChainImages[it], imageViewData, false)
				FrameDataz(
					renderer,
					this,
					im,
				)
			}
		}
	}

	fun acquireNextImage(imageAqSem: Semaphore): Int
	{
		MemoryStack.stackPush().use { stack ->
			val ip = stack.mallocInt(1)
			val err = vkAcquireNextImageKHR(
				device.logicalDevice.vkDevice,
				vkSwapChain,
				0L.inv(),
				imageAqSem.vkSemaphore,
				MemoryUtil.NULL,
				ip,
			)
			if (err == VK_ERROR_OUT_OF_DATE_KHR)
			{
				return -1
			}
			else if (err != VK_SUCCESS)
			{
				// Not optimal but swapchain can still be used
				if (err != VK_SUBOPTIMAL_KHR)
				{
					throw RuntimeException("Failed to acquire image: $err")
				}
			}
			return ip.get(0)
		}
	}

	fun presentImage(queue: CommandSequence, imageIndex: Int): Boolean
	{
		MemoryStack.stackPush().use { stack ->
			val present = VkPresentInfoKHR.calloc(stack)
			present.`sType$Default`()
			present.pWaitSemaphores(stack.longs(
				renderThinger[imageIndex].renderCompleteFlag.vkSemaphore,
			))
			present.swapchainCount(1)
			present.pSwapchains(stack.longs(vkSwapChain))
			present.pImageIndices(stack.ints(imageIndex))
			val err = vkQueuePresentKHR(queue.vkQueue, present)
			if (err == VK_ERROR_OUT_OF_DATE_KHR)
			{
				return true
			}
			else if (err != VK_SUCCESS)
			{
				// Not optimal but swap chain can still be used
				if (err != VK_SUBOPTIMAL_KHR)
				{
					throw RuntimeException("Failed to present KHR: $err")
				}
			}
		}
		return false
	}

	fun free()
	{
		renderThinger.forEach { it.free() }
		vkDestroySwapchainKHR(device.logicalDevice.vkDevice, vkSwapChain, null)
	}
}