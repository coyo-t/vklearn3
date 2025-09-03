package fpw.ren.device

import fpw.ren.GPUtil
import fpw.ren.device.HardwareDevice
import fpw.ren.enums.OSType
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRPortabilitySubset
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkDeviceCreateInfo
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo
import org.lwjgl.vulkan.VkExtensionProperties
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan13Features

class LogicalDevice (val hardware: HardwareDevice)
{
	val vkDevice: VkDevice
	val samplerAnisotropy: Boolean

	init
	{
		MemoryStack.stackPush().use { stack ->
			val reqExtensions = createReqExtensions(stack)
			// Enable all the queue families
			val queuePropsBuff = hardware.vkQueueFamilyProps
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


			// Set up required features
			val features13 = VkPhysicalDeviceVulkan13Features.calloc(stack)
				.`sType$Default`()
				.dynamicRendering(true)
				.synchronization2(true)

			val features2 = VkPhysicalDeviceFeatures2.calloc(stack).`sType$Default`()
			var features = features2.features()

			val supportedFeatures = hardware.vkPhysicalDeviceFeatures
			samplerAnisotropy = supportedFeatures.samplerAnisotropy()
			if (samplerAnisotropy)
			{
//				features.samplerAnisotropy(true)
			}

			features2.pNext(features13.address())

			val deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
				.`sType$Default`()
				.pNext(features2.address())
				.ppEnabledExtensionNames(reqExtensions)
				.pQueueCreateInfos(queueCreationInfoBuf)

			val pp = stack.mallocPointer(1)
			GPUtil.gpuCheck(
				VK10.vkCreateDevice(hardware.vkPhysicalDevice, deviceCreateInfo, null, pp),
				"Failed to create device"
			)
			vkDevice = VkDevice(pp.get(0), hardware.vkPhysicalDevice, deviceCreateInfo)
		}
	}


	private fun createReqExtensions(stack: MemoryStack): PointerBuffer
	{
		val deviceExtensions = getDeviceExtensions()
		val usePortability = (KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME in deviceExtensions) && OSType.Companion.isMacintosh

		val extsList = buildList {
			addAll(HardwareDevice.Companion.REQUIRED_EXTENSIONS.map(stack::ASCII))
			if (usePortability)
			{
				add(stack.ASCII(KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME))
			}
		}
		return stack.mallocPointer(extsList.size).apply {
			extsList.forEach(this::put)
			flip()
		}
	}

	private fun getDeviceExtensions(): Set<String>
	{
		MemoryStack.stackPush().use { stack ->
			val numExtensionsBuf = stack.callocInt(1)
			VK10.vkEnumerateDeviceExtensionProperties(
				hardware.vkPhysicalDevice,
				null as String?,
				numExtensionsBuf,
				null,
			)
			val numExtensions = numExtensionsBuf.get(0)
			val propsBuff = VkExtensionProperties.calloc(numExtensions, stack)
			VK10.vkEnumerateDeviceExtensionProperties(
				hardware.vkPhysicalDevice,
				null as String?,
				numExtensionsBuf,
				propsBuff
			)
			return buildSet {
//				val sb = StringBuilder()
//				sb.append("Device supports [$numExtensions] extensions")
				for (i in 0..<numExtensions)
				{
					val props = propsBuff.get(i)
					val extensionName = props.extensionNameString()
					add(extensionName)
//					sb.appendLine("\t$extensionName")
				}
//				Main.logDebug(sb.toString())
			}
		}
	}

	fun free()
	{
		VK10.vkDestroyDevice(vkDevice, null)
	}

	fun waitIdle()
	{
		VK10.vkDeviceWaitIdle(vkDevice)
	}

}