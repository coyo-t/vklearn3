package fpw.ren

import fpw.FUtil
import fpw.Renderer
import fpw.ren.GPUtil.DBG_CALL_BACK_PREF
import fpw.ren.GPUtil.MESSAGE_SEVERITY_BITMASK
import fpw.ren.GPUtil.MESSAGE_TYPE_BITMASK
import fpw.ren.GPUtil.PORTABILITY_EXTENSION
import fpw.ren.GPUtil.VALIDATION_LAYER
import fpw.ren.GPUtil.gpuCheck
import fpw.ren.enums.OSType
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugUtils.*
import org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
import org.lwjgl.vulkan.VK13.*


class GPUInstance (
	val renderer: Renderer,
	val apiVersion: Int,
	validate: Boolean,
)
{
	var vkInstance: VkInstance
		private set
	private var debugUtils: VkDebugUtilsMessengerCreateInfoEXT? = null
	private var vkDebugHandle = 0L

	init
	{
		MemoryStack.stackPush().use { stack ->
			// Create application information
			val appShortName = stack.UTF8("MACHINE WITNESS")
			val appInfo = VkApplicationInfo.calloc(stack)
			appInfo.`sType$Default`()
			appInfo.pApplicationName(appShortName)
			appInfo.applicationVersion(1)
			appInfo.pEngineName(appShortName)
			appInfo.engineVersion(0)
			appInfo.apiVersion(apiVersion)

			// Validation layers
			val validationLayers = getSupportedValidationLayers()
			val numValidationLayers = validationLayers.size
			var supportsValidation = validate
			if (validate && numValidationLayers == 0)
			{
				supportsValidation = false
				FUtil.logWarn("requested validation but no supported validation layers found :[")
			}

			var requiredLayers: PointerBuffer? = null
			if (supportsValidation)
			{
				requiredLayers = stack.mallocPointer(numValidationLayers)
				for (i in 0..<numValidationLayers)
				{
					val args = validationLayers[i]
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
					FUtil.logInfo(DBG_CALL_BACK_PREF, callbackData.pMessageString())
				}
				else if ((messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0)
				{
					FUtil.logWarn(DBG_CALL_BACK_PREF, callbackData.pMessageString())
				}
				else if ((messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0)
				{
					FUtil.logError(DBG_CALL_BACK_PREF, callbackData.pMessageString())
				}
				else
				{
					FUtil.logDebug(DBG_CALL_BACK_PREF, callbackData.pMessageString())
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
			return List(numExtensions) {
				instanceExtensionsProps.get(it).extensionNameString()
			}.toSet()
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
			val supportedLayers = List(numLayers) {
				propsBuf.get(it).layerNameString()
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

	fun free ()
	{
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
}