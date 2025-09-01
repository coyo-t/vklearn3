package fpw.ren.gpu

import fpw.Renderer
import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkSamplerCreateInfo


class Sampler
{

	val vkSampler: Long

	constructor (vkCtx: Renderer, textureSamplerInfo: Info)
	{
		MemoryStack.stackPush().use { stack ->
			val filter = textureSamplerInfo.filter
			val wrapping = textureSamplerInfo.wrapping
			val samplerInfo = VkSamplerCreateInfo.calloc(stack)
				.`sType$Default`()
				.magFilter(filter.vkEnum)
				.minFilter(filter.vkEnum)
				.addressModeU(wrapping.vkEnum)
				.addressModeV(wrapping.vkEnum)
				.addressModeW(wrapping.vkEnum)
				.borderColor(0)
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
			gpuCheck(vkCreateSampler(vkCtx.vkDevice, samplerInfo, null, lp), "Failed to create sampler")
			vkSampler = lp.get(0)
		}
	}

	fun free(vkCtx: Renderer)
	{
		vkDestroySampler(vkCtx.vkDevice, vkSampler, null)
	}

	data class Info(
		val wrapping: SamplerWrapping,
		val filter: SamplerFilter,
	)
}

