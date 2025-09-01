package fpw.ren.gpu

import fpw.Renderer
import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkImageCreateInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements


class GPUImage
{
	val format: Int
	val mipCount: Int
	val vkImage: Long
	val vkMemory: Long

	constructor (vkCtx: Renderer, imageData: Data)
	{
		MemoryStack.stackPush().use { stack ->
			this.format = imageData.format
			this.mipCount = imageData.mipCount
			val ici = VkImageCreateInfo
				.calloc(stack)
				.`sType$Default`()
				.imageType(VK_IMAGE_TYPE_2D)
				.format(format)
				.extent {
					it.width(imageData.wide)
					it.height(imageData.tall)
					it.depth(1)
				}
				.mipLevels(mipCount)
				.arrayLayers(imageData.arrayLayerCount)
				.samples(imageData.sampleCount)
				.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
				.sharingMode(VK_SHARING_MODE_EXCLUSIVE)
				.tiling(VK_IMAGE_TILING_OPTIMAL)
				.usage(imageData.usage)
			val device = vkCtx.device
			val lp = stack.mallocLong(1)
			gpuCheck(
				vkCreateImage(device.vkDevice, ici, null, lp),
				"gpu image creation",
			)
			vkImage = lp[0]
			val memReqs = VkMemoryRequirements.calloc(stack)
			vkGetImageMemoryRequirements(device.vkDevice, vkImage, memReqs)

			// Select memory size and type
			val memAlloc = VkMemoryAllocateInfo.calloc(stack)
				.`sType$Default`()
				.allocationSize(memReqs.size())
				.memoryTypeIndex(
					GPUtil.memoryTypeFromProperties(
						vkCtx,
						memReqs.memoryTypeBits(), 0
					)
				)
			// Allocate memory
			gpuCheck(
				vkAllocateMemory(device.vkDevice, memAlloc, null, lp),
				"image memory allocate"
			)
			vkMemory = lp.get(0)

			// Bind memory
			gpuCheck(
				vkBindImageMemory(device.vkDevice, vkImage, vkMemory, 0),
				"image memory bind"
			)

		}
	}

	fun free(context: Renderer)
	{
		val d = context.vkDevice
		vkDestroyImage(d, vkImage, null)
		vkFreeMemory(d, vkMemory, null)
	}


	class Data(
		var wide: Int = 0,
		var tall: Int = 0,

		var format: Int = VK_FORMAT_R8G8B8A8_SRGB,
		var mipCount: Int = 1,
		var sampleCount: Int = 1,
		var arrayLayerCount: Int = 1,
		var usage: Int = 0,
	)
}