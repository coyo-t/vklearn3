package fpw.ren

import fpw.Window
import fpw.ren.GPUtil.gpuCheck
import fpw.ren.device.GPUDevice
import fpw.ren.enums.VkFormat
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.KHRSurface
import org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR
import org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.VkSurfaceFormatKHR

class DisplaySurface
{
	val surfaceCaps: VkSurfaceCapabilitiesKHR
	val surfaceFormat: Format
	val vkSurface: Long

	constructor (instance: GPUInstance, gpu: GPUDevice, window: Window)
	{
		stackPush().use { stack ->
			val pSurface = stack.mallocLong(1)
			GLFWVulkan.glfwCreateWindowSurface(
				instance.vkInstance, window.handle,
				null, pSurface
			)
			vkSurface = pSurface.get(0)

			surfaceCaps = VkSurfaceCapabilitiesKHR.calloc()
			gpuCheck(
				KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
					gpu.hardwareDevice.vkPhysicalDevice,
					vkSurface, surfaceCaps
				), "Failed to get surface capabilities"
			)
			surfaceFormat = run {
				val ip = stack.mallocInt(1)
				gpuCheck(
					vkGetPhysicalDeviceSurfaceFormatsKHR(
						gpu.hardwareDevice.vkPhysicalDevice,
						vkSurface, ip, null
					), "Failed to get the number surface formats"
				)
				val numFormats = ip[0]
				require(numFormats > 0) {
					"No surface formats retrieved"
				}

				val surfaceFormats = VkSurfaceFormatKHR.calloc(numFormats, stack)
				gpuCheck(
					vkGetPhysicalDeviceSurfaceFormatsKHR(
						gpu.hardwareDevice.vkPhysicalDevice,
						vkSurface, ip, surfaceFormats
					), "Failed to get surface formats"
				)

				val baseImageFormat = VkFormat.B8G8R8A8_SRGB
				var imageFormat = baseImageFormat.vk
				var colorSpace = surfaceFormats[0].colorSpace()
				for (i in 0..<numFormats)
				{
					val surfaceFormatKHR = surfaceFormats[i]
					if (surfaceFormatKHR.format() == baseImageFormat.vk &&
						surfaceFormatKHR.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
					)
					{
						imageFormat = surfaceFormatKHR.format()
						colorSpace = surfaceFormatKHR.colorSpace()
						break
					}
				}
				Format(VkFormat.byNumeric.getValue(imageFormat), colorSpace)
			}
		}
	}

	fun free (instance: GPUInstance)
	{
		surfaceCaps.free()
		KHRSurface.vkDestroySurfaceKHR(instance.vkInstance, vkSurface, null)
	}

	data class Format(
		val imageFormat: VkFormat,
		val colorSpace: Int,
	)

}