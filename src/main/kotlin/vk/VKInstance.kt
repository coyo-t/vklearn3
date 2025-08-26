package com.catsofwar.vk

import com.catsofwar.vk.VKUtil.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugUtils.*
import org.lwjgl.vulkan.VK13.*
import org.tinylog.kotlin.Logger


class VKInstance (validate: Boolean): AutoCloseable
{
	var vkInstance: VkInstance
		private set
	private lateinit var debugUtils: VkDebugUtilsMessengerCreateInfoEXT
	private var vkDebugHandle = 0L

	init
	{
		Logger.debug("Creating Vulkan instance")
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
				Logger.warn("Request validation but no supported validation layers found. Falling back to no validation")
			}
			Logger.debug("Validation: {}", supportsValidation)

			// Set required  layers
			var requiredLayers: PointerBuffer? = null
			if (supportsValidation)
			{
				requiredLayers = stack.mallocPointer(numValidationLayers)
				for (i in 0..<numValidationLayers)
				{
					Logger.debug("Using validation layer [{}]", validationLayers.get(i))
					requiredLayers.put(i, stack.ASCII(validationLayers.get(i)!!))
				}
			}

			val instanceExtensions = getInstanceExtensions()
			val usePortability = (
				instanceExtensions.contains(PORTABILITY_EXTENSION) &&
				VKUtil.isMacintosh
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
				debugUtils = createDebugCallBack()
				extension = debugUtils.address()
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
				// VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
				instanceInfo.flags(0x00000001)
			}

			val pInstance = stack.mallocPointer(1)
			vkCheck(vkCreateInstance(instanceInfo, null, pInstance), "Error creating instance")
			vkInstance = VkInstance(pInstance[0], instanceInfo)

			vkDebugHandle = VK_NULL_HANDLE
			if (supportsValidation)
			{
				val longBuff = stack.mallocLong(1)
				vkCheck(
					vkCreateDebugUtilsMessengerEXT(vkInstance, debugUtils, null, longBuff),
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
					Logger.info(DBG_CALL_BACK_PREF, callbackData.pMessageString())
				}
				else if ((messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0)
				{
					Logger.warn(DBG_CALL_BACK_PREF, callbackData.pMessageString())
				}
				else if ((messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0)
				{
					Logger.error(DBG_CALL_BACK_PREF, callbackData.pMessageString())
				}
				else
				{
					Logger.debug(DBG_CALL_BACK_PREF, callbackData.pMessageString())
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
			Logger.trace("Instance supports [{}] extensions", numExtensions)

			val instanceExtensionsProps = VkExtensionProperties.calloc(numExtensions, stack)
			vkEnumerateInstanceExtensionProperties(null as String?, numExtensionsBuf, instanceExtensionsProps)
			return buildSet {
				for (i in 0..<numExtensions)
				{
					val props = instanceExtensionsProps.get(i)
					val extensionName = props.extensionNameString()
					add(extensionName)
					Logger.trace("Supported instance extension [{}]", extensionName)
				}
			}
		}
	}

	private fun getSupportedValidationLayers(): List<String>
	{
		MemoryStack.stackPush().use { stack ->
			val numLayersArr = stack.callocInt(1)
			vkEnumerateInstanceLayerProperties(numLayersArr, null)
			val numLayers = numLayersArr[0]
			Logger.debug("Instance supports [{}] layers", numLayers)

			val propsBuf = VkLayerProperties.calloc(numLayers, stack)
			vkEnumerateInstanceLayerProperties(numLayersArr, propsBuf)
			val supportedLayers = buildList {
				for (i in 0..<numLayers)
				{
					val props = propsBuf.get(i)
					val layerName = props.layerNameString()
					add(layerName)
					Logger.trace("Supported layer [{}]", layerName)
				}
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

	override fun close()
	{
		Logger.debug("Destroying Vulkan instance")
		if (vkDebugHandle != VK_NULL_HANDLE)
		{
			vkDestroyDebugUtilsMessengerEXT(vkInstance!!, vkDebugHandle, null)
		}
		vkDestroyInstance(vkInstance!!, null)
		if (debugUtils != null)
		{
			debugUtils!!.pfnUserCallback().free()
			debugUtils!!.free()
		}
	}

	companion object
	{
		val MESSAGE_SEVERITY_BITMASK = (
			VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT or
			VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT or
			0
		)
		val MESSAGE_TYPE_BITMASK = (
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