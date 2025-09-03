package fpw.ren

import fpw.Renderer
import fpw.ren.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO
import org.lwjgl.util.vma.Vma.vmaCreateImage
import org.lwjgl.util.vma.Vma.vmaDestroyImage
import org.lwjgl.util.vma.VmaAllocationCreateInfo
import org.lwjgl.vulkan.VK14.*
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

			val imageCreateInfo = VkImageCreateInfo.calloc(stack)
				.`sType$Default`()
				.imageType(VK_IMAGE_TYPE_2D)
				.format(format)
				.extent {
					it
						.width(imageData.wide)
						.height(imageData.tall)
						.depth(1)
				}
				.mipLevels(1)
				.arrayLayers(imageData.arrayLayerCount)
				.samples(imageData.sampleCount)
				.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
				.sharingMode(VK_SHARING_MODE_EXCLUSIVE)
				.tiling(VK_IMAGE_TILING_OPTIMAL)
				.usage(imageData.usage)

			val allocCreateInfo = VmaAllocationCreateInfo.calloc(1, stack)
				.get(0)
				.usage(VMA_MEMORY_USAGE_AUTO)
				.flags(imageData.memUsage)
				.priority(1.0f)

			val pAllocation = stack.callocPointer(1)
			val lp = stack.mallocLong(1)
			gpuCheck(
				vmaCreateImage(vkCtx.memAlloc.vmaAlloc, imageCreateInfo, allocCreateInfo, lp, pAllocation, null),
				"Failed to create image"
			)
			vkImage = lp.get(0)
			allocation = pAllocation.get(0)
		}
	}

	fun free()
	{
		vmaDestroyImage(vkCtx.memAlloc.vmaAlloc, vkImage, allocation)
//		val d = vkCtx.vkDevice
//		vkDestroyImage(d, vkImage, null)
//		vkFreeMemory(d, vkMemory, null)
	}


	class Data(
		var wide: Int = 0,
		var tall: Int = 0,

		var format: Int = VK_FORMAT_R8G8B8A8_SRGB,
		var mipCount: Int = 1,
		var sampleCount: Int = 1,
		var arrayLayerCount: Int = 1,
		var usage: Int = 0,
		var memUsage: Int = 0,
	)
}