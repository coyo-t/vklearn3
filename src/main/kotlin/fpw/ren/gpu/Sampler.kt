package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkSamplerCreateInfo


class Sampler
{

	val vkSampler: Long

	constructor (vkCtx: GPUContext, textureSamplerInfo: SamplerInfo)
	{
		MemoryStack.stackPush().use { stack ->
			val samplerInfo = VkSamplerCreateInfo.calloc(stack)
				.`sType$Default`()
				.magFilter(VK_FILTER_NEAREST)
				.minFilter(VK_FILTER_NEAREST)
				.addressModeU(textureSamplerInfo.addressMode)
				.addressModeV(textureSamplerInfo.addressMode)
				.addressModeW(textureSamplerInfo.addressMode)
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

	fun free(vkCtx: GPUContext)
	{
		vkDestroySampler(vkCtx.vkDevice, vkSampler, null)
	}
}

