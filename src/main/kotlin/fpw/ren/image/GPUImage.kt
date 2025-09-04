package fpw.ren.image

import fpw.ren.Renderer
import fpw.ren.GPUtil
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.vma.Vma
import org.lwjgl.util.vma.VmaAllocationCreateInfo
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkImageCreateInfo

class GPUImage (val vkCtx: Renderer, imageData: Data)
{
	val format: Int
	val mipCount: Int
	val vkImage: Long
	val allocation: Long

	init
	{
		MemoryStack.stackPush().use { stack ->
			this.format = imageData.format
			this.mipCount = imageData.mipCount

			val ic = VkImageCreateInfo.calloc(stack)
			ic.`sType$Default`()
			ic.imageType(VK10.VK_IMAGE_TYPE_2D)
			ic.format(format)
			ic.extent {
				it.width(imageData.wide)
				it.height(imageData.tall)
				it.depth(1)
			}
			ic.mipLevels(1)
			ic.arrayLayers(imageData.arrayLayerCount)
			ic.samples(imageData.sampleCount)
			ic.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
			ic.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
			ic.tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
			ic.usage(imageData.usage)

			val ac = VmaAllocationCreateInfo.calloc(stack)
			ac.usage(Vma.VMA_MEMORY_USAGE_AUTO)
			ac.flags(imageData.memUsage)
			ac.priority(1.0f)

			val pAllocation = stack.callocPointer(1)
			val lp = stack.mallocLong(1)
			GPUtil.gpuCheck(
				Vma.vmaCreateImage(vkCtx.memAlloc.vmaAlloc, ic, ac, lp, pAllocation, null),
				"Failed to create image"
			)
			vkImage = lp.get(0)
			allocation = pAllocation.get(0)
		}
	}

	fun free()
	{
		Vma.vmaDestroyImage(vkCtx.memAlloc.vmaAlloc, vkImage, allocation)
//		val d = vkCtx.vkDevice
//		vkDestroyImage(d, vkImage, null)
//		vkFreeMemory(d, vkMemory, null)
	}


	class Data(
		var wide: Int = 0,
		var tall: Int = 0,

		var format: Int = VK10.VK_FORMAT_R8G8B8A8_SRGB,
		var mipCount: Int = 1,
		var sampleCount: Int = 1,
		var arrayLayerCount: Int = 1,
		var usage: Int = 0,
		var memUsage: Int = 0,
	)
}