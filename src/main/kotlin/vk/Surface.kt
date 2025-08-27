package com.catsofwar.vk

import com.catsofwar.Main
import com.catsofwar.Window
import com.catsofwar.vk.VKUtil.vkCheck
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRSurface
import org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.VkSurfaceFormatKHR


class Surface (instance: VKInstance, physDevice: PhysicalDevice, window: Window)
{

	val surfaceCaps: VkSurfaceCapabilitiesKHR
	val surfaceFormat: SurfaceFormat
	val vkSurface: Long

	init
	{
		Main.logDebug("Creating Vulkan surface")
		MemoryStack.stackPush().use { stack ->
			val pSurface = stack.mallocLong(1)
			GLFWVulkan.glfwCreateWindowSurface(
				instance.vkInstance, window.handle,
				null, pSurface
			)
			vkSurface = pSurface.get(0)

			surfaceCaps = VkSurfaceCapabilitiesKHR.calloc()
			vkCheck(
				KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
					physDevice.vkPhysicalDevice,
					vkSurface, surfaceCaps
				), "Failed to get surface capabilities"
			)
			surfaceFormat = calcSurfaceFormat(physDevice, vkSurface)
		}
	}

	private fun calcSurfaceFormat(physDevice: PhysicalDevice, vkSurface: Long): SurfaceFormat
	{
		var imageFormat: Int
		var colorSpace: Int
		MemoryStack.stackPush().use { stack ->
			val ip = stack.mallocInt(1)
			vkCheck(
				KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(
					physDevice.vkPhysicalDevice,
					vkSurface, ip, null
				), "Failed to get the number surface formats"
			)
			val numFormats = ip.get(0)
			require(numFormats > 0) {
				"No surface formats retrieved"
			}

			val surfaceFormats = VkSurfaceFormatKHR.calloc(numFormats, stack)
			vkCheck(
				KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(
					physDevice.vkPhysicalDevice,
					vkSurface, ip, surfaceFormats
				), "Failed to get surface formats"
			)

			imageFormat = VK_FORMAT_B8G8R8A8_SRGB
			colorSpace = surfaceFormats.get(0).colorSpace()
			for (i in 0..<numFormats)
			{
				val surfaceFormatKHR = surfaceFormats.get(i)
				if (surfaceFormatKHR.format() == VK_FORMAT_B8G8R8A8_SRGB &&
					surfaceFormatKHR.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
				)
				{
					imageFormat = surfaceFormatKHR.format()
					colorSpace = surfaceFormatKHR.colorSpace()
					break
				}
			}
		}
		return SurfaceFormat(imageFormat, colorSpace)
	}

	fun cleanup (instance: VKInstance)
	{
		Main.logDebug("Destroying Vulkan surface")
		surfaceCaps.free()
		KHRSurface.vkDestroySurfaceKHR(instance.vkInstance, vkSurface, null)
	}

	data class SurfaceFormat(val imageFormat: Int, val colorSpace: Int)

}