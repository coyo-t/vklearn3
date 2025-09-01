package fpw.ren.gpu

import fpw.Renderer
import fpw.TestCube
import fpw.ren.ShaderAssetThinger
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import java.lang.foreign.ValueLayout.JAVA_FLOAT
import java.lang.foreign.ValueLayout.JAVA_INT
import kotlin.io.path.Path


object GPUtil
{

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

	fun createDepthAttachments (vkCtx: Renderer): List<Attachment>
	{
		val swapChain = vkCtx.swapChain
		val numImages: Int = swapChain.numImages
		val swapChainExtent: VkExtent2D = swapChain.swapChainExtent
		return List(numImages) {
			Attachment(
				vkCtx,
				swapChainExtent.width(),
				swapChainExtent.height(),
				VK_FORMAT_D16_UNORM,
				VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
			)
		}
	}

	fun createDepthAttachmentsInfo(
		vkCtx: Renderer,
		depthAttachments: List<Attachment>,
		clearValue: VkClearValue
	): List<VkRenderingAttachmentInfo>
	{
		val swapChain = vkCtx.swapChain
		val numImages = swapChain.numImages
		return List(numImages) {
			VkRenderingAttachmentInfo.calloc()
				.`sType$Default`()
				.imageView(depthAttachments[it].imageView.vkImageView)
				.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
				.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
				.storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
				.clearValue(clearValue)
		}
	}

	fun createPipeline (vkCtx: Renderer, shaderModules: List<ShaderModule>): Pipeline
	{
		val buildInfo = PipelineBuildInfo(
				shaderModules = shaderModules,
				vi = TestCube.format.vi,
				colorFormat = vkCtx.displaySurface.surfaceFormat.imageFormat,
				depthFormat = VK_FORMAT_D16_UNORM,
				pushConstRange = listOf(
					PushConstantRange(VK_SHADER_STAGE_VERTEX_BIT, 0, 128)
				)
			)
		return Pipeline(vkCtx, buildInfo)
	}

	fun createShaderModules(vkCtx: Renderer): List<ShaderModule>
	{
		val srcs = ShaderAssetThinger.loadFromLuaScript(Path("resources/assets/shader/scene.lua"))
		val v = ShaderAssetThinger.compileSPIRV(srcs.vertex, Shaderc.shaderc_glsl_vertex_shader)
		val f = ShaderAssetThinger.compileSPIRV(srcs.fragment, Shaderc.shaderc_glsl_fragment_shader)
		return listOf(
			vkCtx.createShaderModule(VK_SHADER_STAGE_VERTEX_BIT, v),
			vkCtx.createShaderModule(VK_SHADER_STAGE_FRAGMENT_BIT, f),
		)
	}

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

	private fun throwGpuCheck (er:Int, ms:String?): Nothing
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