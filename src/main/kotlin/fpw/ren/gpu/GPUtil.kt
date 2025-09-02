package fpw.ren.gpu

import fpw.Renderer
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import java.awt.Color
import java.lang.foreign.ValueLayout.JAVA_FLOAT
import java.lang.foreign.ValueLayout.JAVA_INT


object GPUtil
{
	const val CW_MASK_RGBA = (
		VK_COLOR_COMPONENT_R_BIT or
		VK_COLOR_COMPONENT_G_BIT or
		VK_COLOR_COMPONENT_B_BIT or
		VK_COLOR_COMPONENT_A_BIT
	)
	val SIZEOF_INT   = JAVA_INT.byteSize().toInt()
	val SIZEOF_FLOAT = JAVA_FLOAT.byteSize().toInt()
	val SIZEOF_MAT4 = SIZEOF_FLOAT*4*4

	fun memoryTypeFromProperties(vkCtx: Renderer, typeBits: Int, reqsMask: Int): Int
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

	fun gpuCheck(err:Int, messageProvider:(Int)->String?)
	{
		if (err != VK_SUCCESS)
		{
			throwGpuCheck(err, messageProvider(err))
		}
	}

	fun gpuCheck(err: Int, errMsg: String?)
	{
		if (err != VK_SUCCESS)
		{
			throwGpuCheck(err, errMsg)
		}
	}

	fun clearTintFrom (c: Color): VkClearValue
	{
		return VkClearValue.calloc().color {
			it
			.float32(0, c.red / 255f)
			.float32(1, c.green / 255f)
			.float32(2, c.blue / 255f)
			.float32(3, c.alpha / 255f)
		}
	}

	fun copyMatrixToBuffer(vkBuffer: GPUBuffer, matrix: Matrix4f, offset: Int)
	{
		val mappedMemory: Long = vkBuffer.map()
		val matrixBuffer = MemoryUtil.memByteBuffer(mappedMemory, vkBuffer.requestedSize.toInt())
		matrix.get(offset, matrixBuffer)
		vkBuffer.unMap()
	}

	private fun throwGpuCheck (er:Int, ms:String?): Nothing
	{
		val errName = ERROR_NAMETABLE.getValue(er)
		val msg = if (ms != null) " '$ms'" else ""
		throw RuntimeException("gpu check failed$msg: #$er - $errName")
	}

}