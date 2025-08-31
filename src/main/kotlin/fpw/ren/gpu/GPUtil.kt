package fpw.ren.gpu

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK13.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkDependencyInfo
import org.lwjgl.vulkan.VkImageMemoryBarrier2
import org.lwjgl.vulkan.VkMemoryType
import org.lwjgl.vulkan.VkRenderingInfo
import java.lang.foreign.ValueLayout.JAVA_FLOAT
import java.lang.foreign.ValueLayout.JAVA_INT


object GPUtil
{

	val SIZEOF_INT   = JAVA_INT.byteSize().toInt()
	val SIZEOF_FLOAT = JAVA_FLOAT.byteSize().toInt()
	val SIZEOF_MAT4 = SIZEOF_FLOAT*4*4

	fun memoryTypeFromProperties(vkCtx: GPUContext, typeBits: Int, reqsMask: Int): Int
	{
		var typeBits = typeBits
		val memoryTypes: VkMemoryType.Buffer = vkCtx.hardware.vkMemoryProperties.memoryTypes()
		for (i in 0..<VK_MAX_MEMORY_TYPES)
		{
			if ((typeBits and 1) == 1 && (memoryTypes[i].propertyFlags() and reqsMask) == reqsMask)
			{
				return i
			}
			typeBits = typeBits shr 1
		}
		throw RuntimeException("failed to find memoryType")
	}

	fun imageBarrier(
		stack: MemoryStack,
		cmdHandle: VkCommandBuffer,
		image: Long,
		oldLayout: Int,
		newLayout: Int,
		srcStage: Long,
		dstStage: Long,
		srcAccess: Long,
		dstAccess: Long,
		aspectMask: Int,
	)
	{
		val imageBarrier = VkImageMemoryBarrier2.calloc(1, stack)
			.`sType$Default`()
			.oldLayout(oldLayout)
			.newLayout(newLayout)
			.srcStageMask(srcStage)
			.dstStageMask(dstStage)
			.srcAccessMask(srcAccess)
			.dstAccessMask(dstAccess)
			.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
			.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
			.subresourceRange {
				it
					.aspectMask(aspectMask)
					.baseMipLevel(0)
					.levelCount(VK_REMAINING_MIP_LEVELS)
					.baseArrayLayer(0)
					.layerCount(VK_REMAINING_ARRAY_LAYERS)
			}
			.image(image)

		val depInfo = VkDependencyInfo.calloc(stack)
			.`sType$Default`()
			.pImageMemoryBarriers(imageBarrier)

		vkCmdPipelineBarrier2(cmdHandle, depInfo)
	}


	val ERROR_NAMETABLE = mapOf(
		VK_SUCCESS to "SUCCESS??? THIS IS BOGUS!!!!!!",
		VK_NOT_READY to "not ready",
		VK_TIMEOUT to "timed out",
		VK_EVENT_SET to "event set",
		VK_EVENT_RESET to "event reset",
		VK_INCOMPLETE to "incomplete",
		VK_ERROR_OUT_OF_HOST_MEMORY to "host out of memory",
		VK_ERROR_OUT_OF_DEVICE_MEMORY to "device out of memory",
		VK_ERROR_INITIALIZATION_FAILED to "initialization failed",
		VK_ERROR_DEVICE_LOST to "lost device",
		VK_ERROR_MEMORY_MAP_FAILED to "memory mapping failed",
		VK_ERROR_LAYER_NOT_PRESENT to "layer isn't present",
		VK_ERROR_EXTENSION_NOT_PRESENT to "extension isn't present",
		VK_ERROR_FEATURE_NOT_PRESENT to "feature isn't present",
		VK_ERROR_INCOMPATIBLE_DRIVER to "driver's incompatible",
		VK_ERROR_TOO_MANY_OBJECTS to "too many objects",
		VK_ERROR_FORMAT_NOT_SUPPORTED to "unsupported format",
		VK_ERROR_FRAGMENTED_POOL to "fragmented pool",
		VK_ERROR_UNKNOWN to "unknown",
	).withDefault { "unmapped??? #$it" }

	fun vkCheck(err:Int, messageProvider:(Int)->String)
	{
		if (err != VK_SUCCESS)
		{
			throwGpuCheck(err, messageProvider(err))
		}
	}

	fun vkCheck(err: Int, errMsg: String?)
	{
		if (err != VK_SUCCESS)
		{
			throwGpuCheck(err, errMsg)
		}
	}

	fun throwGpuCheck (er:Int, ms:String?): Nothing
	{
		val errName = ERROR_NAMETABLE.getValue(er)
		val msg = if (ms != null) " '$ms'" else ""
		throw RuntimeException("gpu check failed$msg: #$er - $errName")
	}

	inline fun renderScoped (cmd: VkCommandBuffer, info: VkRenderingInfo, cm:()->Unit)
	{
		try
		{
			vkCmdBeginRendering(cmd, info)
			cm()
		}
		finally
		{
			vkCmdEndRendering(cmd)
		}
	}
}