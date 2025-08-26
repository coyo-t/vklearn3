package com.catsofwar.vk

import org.lwjgl.vulkan.VK13.*
import sun.awt.OSInfo.OSType

object VKUtil
{
	fun vkCheck(err: Int, errMsg: String?)
	{
		if (err != VK_SUCCESS)
		{
			val errCode = when (err)
			{
				VK_NOT_READY -> "VK_NOT_READY"
				VK_TIMEOUT -> "VK_TIMEOUT"
				VK_EVENT_SET -> "VK_EVENT_SET"
				VK_EVENT_RESET -> "VK_EVENT_RESET"
				VK_INCOMPLETE -> "VK_INCOMPLETE"
				VK_ERROR_OUT_OF_HOST_MEMORY -> "VK_ERROR_OUT_OF_HOST_MEMORY"
				VK_ERROR_OUT_OF_DEVICE_MEMORY -> "VK_ERROR_OUT_OF_DEVICE_MEMORY"
				VK_ERROR_INITIALIZATION_FAILED -> "VK_ERROR_INITIALIZATION_FAILED"
				VK_ERROR_DEVICE_LOST -> "VK_ERROR_DEVICE_LOST"
				VK_ERROR_MEMORY_MAP_FAILED -> "VK_ERROR_MEMORY_MAP_FAILED"
				VK_ERROR_LAYER_NOT_PRESENT -> "VK_ERROR_LAYER_NOT_PRESENT"
				VK_ERROR_EXTENSION_NOT_PRESENT -> "VK_ERROR_EXTENSION_NOT_PRESENT"
				VK_ERROR_FEATURE_NOT_PRESENT -> "VK_ERROR_FEATURE_NOT_PRESENT"
				VK_ERROR_INCOMPATIBLE_DRIVER -> "VK_ERROR_INCOMPATIBLE_DRIVER"
				VK_ERROR_TOO_MANY_OBJECTS -> "VK_ERROR_TOO_MANY_OBJECTS"
				VK_ERROR_FORMAT_NOT_SUPPORTED -> "VK_ERROR_FORMAT_NOT_SUPPORTED"
				VK_ERROR_FRAGMENTED_POOL -> "VK_ERROR_FRAGMENTED_POOL"
				VK_ERROR_UNKNOWN -> "VK_ERROR_UNKNOWN"
				else -> "Not mapped"
			}
			throw RuntimeException("$errMsg: $errCode [$err]")
		}
	}

	val isMacintosh
		get() = getOS() == OSType.MACOSX

	fun getOS (): OSType
	{
		val os = System.getProperty("os.name", "generic").lowercase()
		if ((os.indexOf("mac") >= 0) || (os.indexOf("darwin") >= 0))
		{
			return OSType.MACOSX
		}
		else if (os.indexOf("win") >= 0)
		{
			return OSType.WINDOWS
		}
		else if (os.indexOf("nux") >= 0)
		{
			return OSType.LINUX
		}
		return OSType.UNKNOWN
	}
}