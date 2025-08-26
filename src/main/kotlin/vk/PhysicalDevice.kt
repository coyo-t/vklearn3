package com.catsofwar.vk

import com.catsofwar.vk.VKUtil.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import org.tinylog.kotlin.Logger
import java.util.HashSet


class PhysicalDevice
private constructor (vkPhysicalDevice: VkPhysicalDevice): AutoCloseable
{
	val vkDeviceExtensions: VkExtensionProperties.Buffer
	val vkMemoryProperties: VkPhysicalDeviceMemoryProperties
	val vkPhysicalDevice: VkPhysicalDevice
	val vkPhysicalDeviceFeatures: VkPhysicalDeviceFeatures
	val vkPhysicalDeviceProperties: VkPhysicalDeviceProperties2
	val vkQueueFamilyProps: VkQueueFamilyProperties.Buffer

	init
	{
		MemoryStack.stackPush().use { stack ->
			this.vkPhysicalDevice = vkPhysicalDevice
			val intBuffer = stack.mallocInt(1)

			// Get device properties
			vkPhysicalDeviceProperties = VkPhysicalDeviceProperties2.calloc().`sType$Default`()
			vkGetPhysicalDeviceProperties2(vkPhysicalDevice, vkPhysicalDeviceProperties)

			// Get device extensions
			vkCheck(
				vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, null as String?, intBuffer, null),
				"Failed to get number of device extension properties"
			)
			vkDeviceExtensions = VkExtensionProperties.calloc(intBuffer.get(0))
			vkCheck(
				vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, null as String?, intBuffer, vkDeviceExtensions),
				"Failed to get extension properties"
			)

			// Get Queue family properties
			vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, null)
			vkQueueFamilyProps = VkQueueFamilyProperties.calloc(intBuffer.get(0))
			vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, vkQueueFamilyProps)

			vkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc()
			vkGetPhysicalDeviceFeatures(vkPhysicalDevice, vkPhysicalDeviceFeatures)

			// Get Memory information and properties
			vkMemoryProperties = VkPhysicalDeviceMemoryProperties.calloc()
			vkGetPhysicalDeviceMemoryProperties(vkPhysicalDevice, vkMemoryProperties)
		}
	}

	override fun close ()
	{
		Logger.debug("Destroying physical device [{}]", deviceName)
		vkMemoryProperties.free()
		vkPhysicalDeviceFeatures.free()
		vkQueueFamilyProps.free()
		vkDeviceExtensions.free()
		vkPhysicalDeviceProperties.free()
	}

	val deviceName by lazy {
		vkPhysicalDeviceProperties.properties().deviceNameString()
	}

	private fun hasGraphicsQueueFamily(): Boolean
	{
		var result = false
		val numQueueFamilies = if (vkQueueFamilyProps != null) vkQueueFamilyProps.capacity() else 0
		for (i in 0..<numQueueFamilies)
		{
			val familyProps = vkQueueFamilyProps!!.get(i)
			if ((familyProps.queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0)
			{
				result = true
				break
			}
		}
		return result
	}

	fun supportsExtensions(extensions: MutableSet<String>): Boolean
	{
		val copyExtensions = extensions.toMutableSet()
		val numExtensions = vkDeviceExtensions.capacity()
		for (i in 0..<numExtensions)
		{
			val extensionName = vkDeviceExtensions[i].extensionNameString()
			copyExtensions.remove(extensionName)
		}

		val result = copyExtensions.isEmpty()
		if (!result)
		{
			Logger.debug(
				"At least [{}] extension is not supported by device [{}]",
				copyExtensions.iterator().next(),
				deviceName
			)
		}
		return result
	}

	companion object
	{
		internal val REQUIRED_EXTENSIONS = setOf(
			KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME,
		).toMutableSet()

		internal fun getPhysicalDevices (instance: VKInstance, stack: MemoryStack): PointerBuffer
		{
			val pPhysicalDevices: PointerBuffer
			// Get number of physical devices
			val intBuffer = stack.mallocInt(1)
			vkCheck(
				vkEnumeratePhysicalDevices(instance.vkInstance, intBuffer, null),
				"Failed to get number of physical devices"
			)
			val numDevices = intBuffer.get(0)
			Logger.debug("Detected {} physical device(s)", numDevices)

			// Populate physical devices list pointer
			pPhysicalDevices = stack.mallocPointer(numDevices)
			vkCheck(
				vkEnumeratePhysicalDevices(instance.vkInstance, intBuffer, pPhysicalDevices),
				"Failed to get physical devices"
			)
			return pPhysicalDevices
		}

		fun createPhysicalDevice(instance: VKInstance, prefDeviceName: String?): PhysicalDevice
		{
			Logger.debug("Selecting physical devices")
			var result: PhysicalDevice? = null
			MemoryStack.stackPush().use { stack ->
				// Get available devices
				val pPhysicalDevices = getPhysicalDevices(instance, stack)
				val numDevices = pPhysicalDevices.capacity()

				// Populate available devices
				val physDevices = buildList {
					for (i in 0..<numDevices)
					{
						val vkPhysicalDevice = VkPhysicalDevice(pPhysicalDevices.get(i), instance.vkInstance)
						val physDevice = PhysicalDevice(vkPhysicalDevice)

						val deviceName = physDevice.deviceName
						if (!physDevice.hasGraphicsQueueFamily())
						{
							Logger.debug("Device [{}] does not support graphics queue family", deviceName)
							physDevice.close()
							continue
						}

						if (!physDevice.supportsExtensions(REQUIRED_EXTENSIONS))
						{
							Logger.debug("Device [{}] does not support required extensions", deviceName)
							physDevice.close()
							continue
						}

						if (prefDeviceName != null && prefDeviceName == deviceName)
						{
							result = physDevice
							break
						}
						if (physDevice.vkPhysicalDeviceProperties.properties().deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU)
						{
							addFirst(physDevice)
						}
						else
						{
							add(physDevice)
						}
					}
				}.toMutableList()

				// No preferred device or it does not meet requirements, just pick the first one
				result = if (result == null && !physDevices.isEmpty()) physDevices.removeFirst() else result

				// Clean up non-selected devices
				physDevices.forEach(PhysicalDevice::close)
				Logger.debug("Selected device: [{}]", result?.deviceName)
				return requireNotNull(result) {
					"No suitable physical devices found"
				}
			}
		}
	}

}

