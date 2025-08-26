package com.catsofwar.vk

import com.catsofwar.vk.VKUtil.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME
import org.lwjgl.vulkan.VK13.*
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkDeviceCreateInfo
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo
import org.lwjgl.vulkan.VkExtensionProperties
import org.tinylog.kotlin.Logger

class Device (physDevice: PhysicalDevice): AutoCloseable
{
	val vkDevice: VkDevice

	init
	{
		Logger.debug("Creating device")

		MemoryStack.stackPush().use { stack ->
			val reqExtensions = createReqExtensions(physDevice, stack)
			// Enable all the queue families
			val queuePropsBuff = physDevice.vkQueueFamilyProps
			val numQueuesFamilies = queuePropsBuff.capacity()
			val queueCreationInfoBuf = VkDeviceQueueCreateInfo.calloc(numQueuesFamilies, stack)
			for (i in 0..<numQueuesFamilies)
			{
				val priorities = stack.callocFloat(queuePropsBuff.get(i).queueCount())
				queueCreationInfoBuf.get(i)
					.`sType$Default`()
					.queueFamilyIndex(i)
					.pQueuePriorities(priorities)
			}

			val deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
				.`sType$Default`()
				.ppEnabledExtensionNames(reqExtensions)
				.pQueueCreateInfos(queueCreationInfoBuf)

			val pp = stack.mallocPointer(1)
			vkCheck(
				vkCreateDevice(physDevice.vkPhysicalDevice, deviceCreateInfo, null, pp),
				"Failed to create device"
			)
			vkDevice = VkDevice(pp.get(0), physDevice.vkPhysicalDevice, deviceCreateInfo)
		}
	}

	private fun createReqExtensions(physDevice: PhysicalDevice, stack: MemoryStack): PointerBuffer
	{
		val deviceExtensions = getDeviceExtensions(physDevice)
		val usePortability = (VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME in deviceExtensions) && VKUtil.OSType.isMacintosh

		val extsList = buildList {
			addAll(PhysicalDevice.REQUIRED_EXTENSIONS.map(stack::ASCII))
			if (usePortability)
			{
				add(stack.ASCII(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME))
			}
		}
		return stack.mallocPointer(extsList.size).apply {
			extsList.forEach(this::put)
			flip()
		}
	}

	private fun getDeviceExtensions(physDevice: PhysicalDevice): Set<String>
	{
		MemoryStack.stackPush().use { stack ->
			val numExtensionsBuf = stack.callocInt(1)
			vkEnumerateDeviceExtensionProperties(physDevice.vkPhysicalDevice, null as String?, numExtensionsBuf, null)
			val numExtensions = numExtensionsBuf.get(0)
			val propsBuff = VkExtensionProperties.calloc(numExtensions, stack)
			vkEnumerateDeviceExtensionProperties(
				physDevice.vkPhysicalDevice,
				null as String?,
				numExtensionsBuf,
				propsBuff
			)
			return buildSet {
				val sb = StringBuilder()
				sb.appendLine("Device supports [$numExtensions] extensions:")
				for (i in 0..<numExtensions)
				{
					val props = propsBuff.get(i)
					val extensionName = props.extensionNameString()
					add(extensionName)
					sb.appendLine("\textensionName")
				}
				Logger.trace(sb.toString())
			}
		}
	}

	override fun close()
	{
		Logger.debug("Destroying Vulkan device")
		vkDestroyDevice(vkDevice, null)
	}

	fun waitIdle()
	{
		vkDeviceWaitIdle(vkDevice)
	}
}