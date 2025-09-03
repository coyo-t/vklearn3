package fpw.ren

import fpw.FUtil
import fpw.Image
import fpw.Renderer
import fpw.ren.GPUtil.imageBarrier
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT
import org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO
import org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
import org.lwjgl.vulkan.VK10.vkCmdCopyBufferToImage
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkBufferImageCopy.calloc


class Texture
{

	val wide: Int
	val tall: Int
	val id: String
	val image: GPUImage
	val imageView: ImageView
	private var recordedTransition: Boolean
	private var stgBuffer: GPUBuffer?

	constructor (vkCtx: Renderer, id: String, srcImage: Image, imageFormat: Int)
	{
		this.id = id
		recordedTransition = false
		wide = srcImage.wide
		tall = srcImage.tall

		stgBuffer = run {
			val size = srcImage.data.byteSize()
			val stgBuffer = GPUBuffer(
				vkCtx,
				size,
				VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
				VMA_MEMORY_USAGE_AUTO,
				VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
				VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
			)
			stgBuffer.doMapped {
				val buffer = FUtil.createMemoryAt(it, stgBuffer.requestedSize)
				buffer.copyFrom(srcImage.data)
			}
			stgBuffer
		}
		image = GPUImage(
			vkCtx,
			GPUImage.Data(
				wide = wide,
				tall = tall,
				usage = (
					VK_IMAGE_USAGE_TRANSFER_SRC_BIT or
					VK_IMAGE_USAGE_TRANSFER_DST_BIT or
					VK_IMAGE_USAGE_SAMPLED_BIT
				),
				format = imageFormat
			),
		)
		imageView = ImageView(
			vkCtx.gpu.logicalDevice,
			image.vkImage,
			ImageView.Data(
				format = image.format,
				aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
			),
			false,
		)
	}

	fun free()
	{
		cleanupStgBuffer()
		imageView.free()
		image.free()
	}

	fun cleanupStgBuffer()
	{
		stgBuffer?.let {
			it.free()
			stgBuffer = null
		}
	}

	fun recordTextureTransition (cmd: CommandBuffer)
	{
		val staging = stgBuffer
		if (staging != null && !recordedTransition)
		{
			recordedTransition = true
			MemoryStack.stackPush().use { stack ->
				imageBarrier(
					stack,
					cmd.vkCommandBuffer,
					image.vkImage,
					VK_IMAGE_LAYOUT_UNDEFINED,
					VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
					VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.toLong(),
					VK_PIPELINE_STAGE_TRANSFER_BIT.toLong(),
					VK_ACCESS_2_NONE,
					VK_ACCESS_TRANSFER_WRITE_BIT.toLong(),
					VK_IMAGE_ASPECT_COLOR_BIT
				)
				val region = calloc(1, stack)
					.bufferOffset(0)
					.bufferRowLength(0)
					.bufferImageHeight(0)
					.imageSubresource {
						it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
							.mipLevel(0)
							.baseArrayLayer(0)
							.layerCount(1)
					}
					.imageOffset { it.x(0).y(0).z(0) }
					.imageExtent { it.width(wide).height(tall).depth(1) }
				vkCmdCopyBufferToImage(
					cmd.vkCommandBuffer,
					staging.bufferStruct,
					image.vkImage,
					VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
					region,
				)
				imageBarrier(
					stack,
					cmd.vkCommandBuffer,
					image.vkImage,
					VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
					VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
					VK_PIPELINE_STAGE_TRANSFER_BIT.toLong(),
					VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.toLong(),
					VK_ACCESS_TRANSFER_WRITE_BIT.toLong(),
					VK_ACCESS_SHADER_READ_BIT.toLong(),
					VK_IMAGE_ASPECT_COLOR_BIT
				)
			}
		}
	}

}