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
			val samplerInfo = VkSamplerCreateInfo.calloc(stack)
				.`sType$Default`()
				.magFilter(textureSamplerInfo.filter.vkEnum)
				.minFilter(textureSamplerInfo.filter.vkEnum)
				.addressModeU(textureSamplerInfo.wrapping.vkEnum)
				.addressModeV(textureSamplerInfo.wrapping.vkEnum)
				.addressModeW(textureSamplerInfo.wrapping.vkEnum)
				.borderColor(textureSamplerInfo.borderColor)
				.unnormalizedCoordinates(false)
				.compareEnable(false)
				.compareOp(VK_COMPARE_OP_NEVER)
				.mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
				.minLod(0.0f)
				.maxLod(textureSamplerInfo.mipLevels.toFloat())
				.mipLodBias(0.0f)
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
		val borderColor: Int,
		val mipLevels: Int,
		val anisotropicFiltering: Boolean
	)
}

