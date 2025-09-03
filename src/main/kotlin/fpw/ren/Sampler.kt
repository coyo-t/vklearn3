package fpw.ren

import fpw.Renderer
import fpw.ren.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkSamplerCreateInfo


class Sampler (
	val vkCtx: Renderer,
	wrapping: SamplerWrapping,
	filter: SamplerFilter,
)
{
	val vkSampler = MemoryStack.stackPush().use { stack ->
		val filter = filter
		val wrapping = wrapping
		val samplerInfo = VkSamplerCreateInfo.calloc(stack)
			.`sType$Default`()
			.magFilter(filter.vkEnum)
			.minFilter(filter.vkEnum)
			.addressModeU(wrapping.vkEnum)
			.addressModeV(wrapping.vkEnum)
			.addressModeW(wrapping.vkEnum)
			.borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
			.unnormalizedCoordinates(false)
			.compareEnable(false)
			.compareOp(VK_COMPARE_OP_NEVER)
			.mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
			.minLod(0f)
			.maxLod(0f)
			.mipLodBias(0f)
//			if (textureSamplerInfo.anisotropy && vkCtx.device.samplerAnisotropy)
//			{
//				val MAX_ANISOTROPY = 16
//				samplerInfo
//					.anisotropyEnable(true)
//					.maxAnisotropy(MAX_ANISOTROPY.toFloat())
//			}

		val lp = stack.mallocLong(1)
		gpuCheck(vkCreateSampler(vkCtx.gpu.logicalDevice.vkDevice, samplerInfo, null, lp), "Failed to create sampler")
		lp[0]
	}


	fun free ()
	{
		vkDestroySampler(vkCtx.gpu.logicalDevice.vkDevice, vkSampler, null)
	}
}

