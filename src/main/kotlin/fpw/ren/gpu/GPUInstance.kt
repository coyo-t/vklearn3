package fpw.ren.gpu

import fpw.Main
import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugUtils.*
import org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
import org.lwjgl.vulkan.VK13.*


class GPUInstance (validate: Boolean)
{
	var vkInstance: VkInstance
		private set
	private var debugUtils: VkDebugUtilsMessengerCreateInfoEXT? = null
	private var vkDebugHandle = 0L

	init
	{
//		Main.logDebug("Creating Vulkan instance")
		MemoryStack.stackPush().use { stack ->
			// Create application information
			val appShortName = stack.UTF8("VulkanBook")
			val appInfo = VkApplicationInfo.calloc(stack)
				.`sType$Default`()
				.pApplicationName(appShortName)
				.applicationVersion(1)
				.pEngineName(appShortName)
				.engineVersion(0)
				.apiVersion(VK_API_VERSION_1_3)

			// Validation layers
			val validationLayers = getSupportedValidationLayers()
			val numValidationLayers = validationLayers.size
			var supportsValidation = validate
			if (validate && numValidationLayers == 0)
			{
				supportsValidation = false
				Main.logWarn("requested validation but no supported validation layers found :[")
			}
//			Main.logDebug("gpu validation: $supportsValidation")

			// Set required  layers
			var requiredLayers: PointerBuffer? = null
			if (supportsValidation)
			{
				requiredLayers = stack.mallocPointer(numValidationLayers)
				for (i in 0..<numValidationLayers)
				{
					val args = validationLayers[i]
//					Main.logDebug("using validation layer [$args]")
					requiredLayers.put(i, stack.ASCII(args))
				}
			}

			val instanceExtensions = getInstanceExtensions()
			val usePortability = (
				instanceExtensions.contains(PORTABILITY_EXTENSION) &&
				OSType.isMacintosh
			)

			// GLFW Extension
			val glfwExtensions = requireNotNull(GLFWVulkan.glfwGetRequiredInstanceExtensions()) {
				"Failed to find the GLFW platform surface extensions"
			}
			val additionalExtensions = buildList {
				if (supportsValidation)
				{
					add(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME))
				}
				if (usePortability)
				{
					add(stack.UTF8(PORTABILITY_EXTENSION))
				}
			}
			val numAdditionalExtensions = additionalExtensions.size
			val requiredExtensions = stack.mallocPointer(glfwExtensions.remaining() + numAdditionalExtensions)
			requiredExtensions.put(glfwExtensions)
			for (i in 0..<numAdditionalExtensions)
			{
				requiredExtensions.put(additionalExtensions[i])
			}
			requiredExtensions.flip()

			var extension = MemoryUtil.NULL
			if (supportsValidation)
			{
				val fuck = createDebugCallBack()
				debugUtils = fuck
				extension = fuck.address()
			}

			// Create instance info
			val instanceInfo = VkInstanceCreateInfo.calloc(stack)
				.`sType$Default`()
				.pNext(extension)
				.pApplicationInfo(appInfo)
				.ppEnabledLayerNames(requiredLayers)
				.ppEnabledExtensionNames(requiredExtensions)
			if (usePortability)
			{
//				instanceInfo.flags(0x00000001)
				instanceInfo.flags(VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR)
			}

			val pInstance = stack.mallocPointer(1)
			gpuCheck(vkCreateInstance(instanceInfo, null, pInstance), "Error creating instance")
			vkInstance = VkInstance(pInstance[0], instanceInfo)

			vkDebugHandle = VK_NULL_HANDLE
			if (supportsValidation)
			{
				val longBuff = stack.mallocLong(1)
				gpuCheck(
					vkCreateDebugUtilsMessengerEXT(vkInstance, debugUtils!!, null, longBuff),
					"Error creating debug utils"
				)
				vkDebugHandle = longBuff[0]
			}
		}
	}

	private fun createDebugCallBack(): VkDebugUtilsMessengerCreateInfoEXT
	{
		return VkDebugUtilsMessengerCreateInfoEXT
			.calloc()
			.`sType$Default`()
			.messageSeverity(MESSAGE_SEVERITY_BITMASK)
			.messageType(MESSAGE_TYPE_BITMASK)
			.pfnUserCallback { messageSeverity, messageTypes, pCallbackData, pUserData ->
				val callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData)
				if ((messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0)
				{
					Main.logInfo(DBG_CALL_BACK_PREF, callbackData.pMessageString())
				}
				else if ((messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0)
				{
					Main.logWarn(DBG_CALL_BACK_PREF, callbackData.pMessageString())
				}
				else if ((messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0)
				{
					Main.logError(DBG_CALL_BACK_PREF, callbackData.pMessageString())
				}
				else
				{
					Main.logDebug(DBG_CALL_BACK_PREF, callbackData.pMessageString())
				}
				VK_FALSE
			}
	}

	private fun getInstanceExtensions(): Set<String>
	{
		MemoryStack.stackPush().use { stack ->
			val numExtensionsBuf = stack.callocInt(1)
			vkEnumerateInstanceExtensionProperties(null as String?, numExtensionsBuf, null)
			val numExtensions = numExtensionsBuf.get(0)
			val instanceExtensionsProps = VkExtensionProperties.calloc(numExtensions, stack)
			vkEnumerateInstanceExtensionProperties(null as String?, numExtensionsBuf, instanceExtensionsProps)
			return buildSet {
//				val sb = StringBuilder()
//				sb.append("Instance supports [$numExtensions] extensions")
				for (i in 0..<numExtensions)
				{
					val props = instanceExtensionsProps.get(i)
					val extensionName = props.extensionNameString()
					add(extensionName)
//					sb.appendLine("\t$extensionName")
				}
//				Main.logDebug(sb.toString())
			}
		}
	}

	private fun getSupportedValidationLayers(): List<String>
	{
		MemoryStack.stackPush().use { stack ->
			val numLayersArr = stack.callocInt(1)
			vkEnumerateInstanceLayerProperties(numLayersArr, null)
			val numLayers = numLayersArr[0]
			val propsBuf = VkLayerProperties.calloc(numLayers, stack)
			vkEnumerateInstanceLayerProperties(numLayersArr, propsBuf)
			val supportedLayers = buildList {
//				val sb = StringBuilder()
//				sb.append("Instance supports [$numLayers] layers")
				for (i in 0..<numLayers)
				{
					val props = propsBuf.get(i)
					val layerName = props.layerNameString()
					add(layerName)
//					sb.appendLine("\t$layerName")
				}
//				Main.logDebug(sb.toString())
			}

			// Main validation layer
			return buildList {
				if (supportedLayers.contains(VALIDATION_LAYER))
				{
					add(VALIDATION_LAYER)
				}
			}
		}
	}

	fun close()
	{
//		Main.logDebug("Destroying Vulkan instance")
		if (vkDebugHandle != VK_NULL_HANDLE)
		{
			vkDestroyDebugUtilsMessengerEXT(vkInstance, vkDebugHandle, null)
		}
		vkDestroyInstance(vkInstance, null)
		debugUtils?.apply {
			pfnUserCallback().free()
			free()
		}
	}

	companion object
	{
		const val MESSAGE_SEVERITY_BITMASK = (
			VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT or
			VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT or
			0
		)
		const val MESSAGE_TYPE_BITMASK = (
			VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or
			VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or
			VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT or
			0
		)
		const val DBG_CALL_BACK_PREF = "VkDebugUtilsCallback, {}"
		const val PORTABILITY_EXTENSION = "VK_KHR_portability_enumeration"
		const val VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation"

	}

}