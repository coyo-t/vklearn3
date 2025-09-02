package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME
import org.lwjgl.vulkan.VK13.*


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
			gpuCheck(
				vkCreateDevice(hardware.vkPhysicalDevice, deviceCreateInfo, null, pp),
				"Failed to create device"
			)
			vkDevice = VkDevice(pp.get(0), hardware.vkPhysicalDevice, deviceCreateInfo)
		}
	}


	private fun createReqExtensions(stack: MemoryStack): PointerBuffer
	{
		val deviceExtensions = getDeviceExtensions()
		val usePortability = (VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME in deviceExtensions) && OSType.isMacintosh

		val extsList = buildList {
			addAll(HardwareDevice.REQUIRED_EXTENSIONS.map(stack::ASCII))
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

	private fun getDeviceExtensions(): Set<String>
	{
		MemoryStack.stackPush().use { stack ->
			val numExtensionsBuf = stack.callocInt(1)
			vkEnumerateDeviceExtensionProperties(
				hardware.vkPhysicalDevice,
				null as String?,
				numExtensionsBuf,
				null,
			)
			val numExtensions = numExtensionsBuf.get(0)
			val propsBuff = VkExtensionProperties.calloc(numExtensions, stack)
			vkEnumerateDeviceExtensionProperties(
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
		vkDestroyDevice(vkDevice, null)
	}

	fun waitIdle()
	{
		vkDeviceWaitIdle(vkDevice)
	}

	fun createPipelineCache(): PipelineCache
	{
		val outs = MemoryStack.stackPush().use { stack ->
			val createInfo = VkPipelineCacheCreateInfo.calloc(stack).`sType$Default`()
			val lp = stack.mallocLong(1)
			gpuCheck(
				vkCreatePipelineCache(vkDevice, createInfo, null, lp),
				"Error creating pipeline cache"
			)
			lp.get(0)
		}
		return PipelineCache(outs)
	}
}