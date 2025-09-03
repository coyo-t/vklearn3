package fpw.ren

import fpw.Renderer
import fpw.ren.GPUtil.gpuCheck
import fpw.ren.enums.SamplerFilter
import fpw.ren.enums.SamplerWrapping
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkSamplerCreateInfo


class Sampler (
	val vkCtx: Renderer,
	wrapping: SamplerWrapping,
	filter: SamplerFilter,
)
{
	val vkSampler: Long

	init
	{
		MemoryStack.stackPush().use { stack ->
			val filter = filter
			val wrapping = wrapping
			val samplerInfo = VkSamplerCreateInfo.calloc(stack)
			samplerInfo.`sType$Default`()
			samplerInfo.magFilter(filter.vkEnum)
			samplerInfo.minFilter(filter.vkEnum)
			samplerInfo.addressModeU(wrapping.vkEnum)
			samplerInfo.addressModeV(wrapping.vkEnum)
			samplerInfo.addressModeW(wrapping.vkEnum)
			samplerInfo.borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
			samplerInfo.unnormalizedCoordinates(false)
			samplerInfo.compareEnable(false)
			samplerInfo.compareOp(VK_COMPARE_OP_NEVER)
			samplerInfo.mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
			samplerInfo.minLod(0f)
			samplerInfo.maxLod(0f)
			samplerInfo.mipLodBias(0f)
//			if (textureSamplerInfo.anisotropy && vkCtx.device.samplerAnisotropy)
//			{
//				val MAX_ANISOTROPY = 16
//				samplerInfo
//					.anisotropyEnable(true)
//					.maxAnisotropy(MAX_ANISOTROPY.toFloat())
//			}

			val lp = stack.mallocLong(1)
			gpuCheck(vkCreateSampler(vkCtx.gpu.logicalDevice.vkDevice, samplerInfo, null, lp), "Failed to create sampler")
			vkSampler = lp[0]
		}
	}


	fun free ()
	{
		vkDestroySampler(vkCtx.gpu.logicalDevice.vkDevice, vkSampler, null)
	}
}

