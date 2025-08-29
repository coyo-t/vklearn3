package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkImageCreateInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements


class GPUImage: GPUClosable
{
	val format: Int
	val mipCount: Int
	val vkImage: Long
	val vkMemory: Long

	constructor (vkCtx: GPUContext, imageData: Data)
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
			vkCheck(vkCreateImage(device.vkDevice, ici, null, lp), "gpu image creation failure")
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
			vkCheck(
				vkAllocateMemory(device.vkDevice, memAlloc, null, lp),
				"Failed to allocate memory"
			)
			vkMemory = lp.get(0)

			// Bind memory
			vkCheck(
				vkBindImageMemory(device.vkDevice, vkImage, vkMemory, 0),
				"Failed to bind image memory"
			)

		}
	}

	override fun close(context: GPUContext)
	{
		val d = context.vkDevice
		vkDestroyImage(d, vkImage, null);
		vkFreeMemory(d, vkMemory, null);
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
	{
	}
}