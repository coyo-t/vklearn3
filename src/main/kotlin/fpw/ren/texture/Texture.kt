package fpw.ren.texture

import fpw.FUtil
import fpw.Image
import fpw.ren.Renderer
import fpw.ren.GPUBuffer
import fpw.ren.image.GPUImage
import fpw.ren.GPUtil
import fpw.ren.image.ImageView
import fpw.ren.command.CommandBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.vma.Vma
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK13
import org.lwjgl.vulkan.VkBufferImageCopy

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
				VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
				Vma.VMA_MEMORY_USAGE_AUTO,
				Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
				VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
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
						  VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT or
									 VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT or
									 VK10.VK_IMAGE_USAGE_SAMPLED_BIT
						  ),
				format = imageFormat
			),
		)
		imageView = ImageView(
			vkCtx.gpu,
			image.vkImage,
			ImageView.Data(
				format = image.format,
				aspectMask = VK10.VK_IMAGE_ASPECT_COLOR_BIT,
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
				GPUtil.imageBarrier(
					stack,
					cmd.vkCommandBuffer,
					image.vkImage,
					VK10.VK_IMAGE_LAYOUT_UNDEFINED,
					VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
					VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.toLong(),
					VK10.VK_PIPELINE_STAGE_TRANSFER_BIT.toLong(),
					VK13.VK_ACCESS_2_NONE,
					VK10.VK_ACCESS_TRANSFER_WRITE_BIT.toLong(),
					VK10.VK_IMAGE_ASPECT_COLOR_BIT
				)
				val region = VkBufferImageCopy.calloc(1, stack)
					.bufferOffset(0)
					.bufferRowLength(0)
					.bufferImageHeight(0)
					.imageSubresource {
						it.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
							.mipLevel(0)
							.baseArrayLayer(0)
							.layerCount(1)
					}
					.imageOffset { it.x(0).y(0).z(0) }
					.imageExtent { it.width(wide).height(tall).depth(1) }
				VK10.vkCmdCopyBufferToImage(
					cmd.vkCommandBuffer,
					staging.bufferStruct,
					image.vkImage,
					VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
					region,
				)
				GPUtil.imageBarrier(
					stack,
					cmd.vkCommandBuffer,
					image.vkImage,
					VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
					VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
					VK10.VK_PIPELINE_STAGE_TRANSFER_BIT.toLong(),
					VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.toLong(),
					VK10.VK_ACCESS_TRANSFER_WRITE_BIT.toLong(),
					VK10.VK_ACCESS_SHADER_READ_BIT.toLong(),
					VK10.VK_IMAGE_ASPECT_COLOR_BIT
				)
			}
		}
	}

}