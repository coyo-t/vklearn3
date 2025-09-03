package fpw.ren

import fpw.FUtil
import fpw.Renderer
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.nmemFree
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugUtils.*
import org.lwjgl.vulkan.VK13.*
import java.awt.Color
import java.lang.foreign.ValueLayout.JAVA_FLOAT
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.ref.Cleaner


object GPUtil
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
	const val DBG_CALL_BACK_PREF = "VkDebugUtilsCallback\n%s\n"
	const val PORTABILITY_EXTENSION = "VK_KHR_portability_enumeration"
	const val VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation"


	const val CW_MASK_RGBA = (
		VK_COLOR_COMPONENT_R_BIT or
		VK_COLOR_COMPONENT_G_BIT or
		VK_COLOR_COMPONENT_B_BIT or
		VK_COLOR_COMPONENT_A_BIT
	)
	val SIZEOF_INT   = JAVA_INT.byteSize().toInt()
	val SIZEOF_FLOAT = JAVA_FLOAT.byteSize().toInt()
	val SIZEOF_MAT4 = SIZEOF_FLOAT*4*4

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

	val CLEANER = Cleaner.create()

	class PointerCleanerUpper (
		val nameTag: String?,
		val ptrs: LongArray,
	): Runnable
	{
		constructor(nameTag: String?, addr: Long):
			this(nameTag, longArrayOf(addr))

		override fun run()
		{
			nameTag?.run {
				FUtil.logInfo("FREEING $nameTag :)")
			}
			for (it in ptrs)
			{
				nmemFree(it)
			}
		}
	}

	fun <T: Pointer.Default> registerPointerForCleanup (who: T): T
	{
		CLEANER.register(who, PointerCleanerUpper(null, who.address()))
		return who
	}

	fun registerPointersForCleanup (who: Any, vararg ptr: Long)
	{
		CLEANER.register(who, PointerCleanerUpper(who.javaClass.toString(), ptr))
	}

	fun registerPointersForCleanup (who:Any, vararg ptr: Pointer.Default)
	{
		CLEANER.register(
			who,
			PointerCleanerUpper(
				who.javaClass.toString(),
				ptr.map { it.address() }.toLongArray()
			)
		)
	}

	fun forceCleanup ()
	{
		System.gc()
	}

	fun memoryTypeFromProperties(vkCtx: Renderer, typeBits: Int, reqsMask: Int): Int
	{
		var typeBits = typeBits
		val memoryTypes: VkMemoryType.Buffer = vkCtx.gpu.hardwareDevice.vkMemoryProperties.memoryTypes()
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
		imageBarrier.`sType$Default`()
		imageBarrier.oldLayout(oldLayout)
		imageBarrier.newLayout(newLayout)
		imageBarrier.srcStageMask(srcStage)
		imageBarrier.dstStageMask(dstStage)
		imageBarrier.srcAccessMask(srcAccess)
		imageBarrier.dstAccessMask(dstAccess)
		imageBarrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
		imageBarrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
		imageBarrier.subresourceRange {
			it.aspectMask(aspectMask)
			it.baseMipLevel(0)
			it.levelCount(VK_REMAINING_MIP_LEVELS)
			it.baseArrayLayer(0)
			it.layerCount(VK_REMAINING_ARRAY_LAYERS)
		}
		imageBarrier.image(image)

		val dependencyInfo = VkDependencyInfo.calloc(stack)
		dependencyInfo.`sType$Default`()
		dependencyInfo.pImageMemoryBarriers(imageBarrier)

		vkCmdPipelineBarrier2(cmdHandle, dependencyInfo)
	}

	fun gpuCheck(err:Int, messageProvider:(Int)->String?)
	{
		if (err != VK_SUCCESS)
		{
			gpuCheck(err, messageProvider(err))
		}
	}

	fun gpuCheck(err: Int, errMsg: String?)
	{
		if (err != VK_SUCCESS)
		{
			val errName = ERROR_NAMETABLE.getValue(err)
			val msg = if (errMsg != null) " '$errMsg'" else ""
			throw RuntimeException("gpu check failed$msg: #$err - $errName")
		}
	}

	fun createClearValue (): VkClearValue
	{
		return registerPointerForCleanup(VkClearValue.calloc())
	}

	fun createClearValue (c: Color): VkClearValue
	{
		return createClearValue().color {
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

}