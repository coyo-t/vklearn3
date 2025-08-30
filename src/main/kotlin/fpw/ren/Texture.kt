package fpw.ren

import fpw.FUtil
import fpw.Image
import fpw.ren.gpu.*
import fpw.ren.gpu.GPUtil.imageBarrier
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkBufferImageCopy
import java.lang.foreign.MemorySegment


class Texture
{

	private val width: Int
	private val height: Int
	private val id: String
	private val image: GPUImage
	private val imageView: ImageView
	private var recordedTransition: Boolean
	private var stgBuffer: GPUBuffer?

	constructor (vkCtx: GPUContext, id: String, srcImage: Image, imageFormat: Int)
	{
		this.id = id
		recordedTransition = false
		width = srcImage.wide
		height = srcImage.tall

		stgBuffer = createStgBuffer(vkCtx, srcImage.data)
		val imageData = GPUImage.Data(
			wide=width,
			tall=height,
			usage=(
				VK_IMAGE_USAGE_TRANSFER_SRC_BIT or
				VK_IMAGE_USAGE_TRANSFER_DST_BIT or
				VK_IMAGE_USAGE_SAMPLED_BIT
			),
			format=imageFormat
		)
		image = GPUImage(vkCtx, imageData)
		val imageViewData = ImageViewData(
			format=image.format,
			aspectMask=VK_IMAGE_ASPECT_COLOR_BIT,
		)
		imageView = ImageView(
			vkCtx.device,
			image.vkImage,
			imageViewData,
			false,
		)
	}

	fun cleanup(vkCtx: GPUContext)
	{
		cleanupStgBuffer(vkCtx)
		imageView.close(vkCtx.device)
		image.close(vkCtx)
	}

	fun cleanupStgBuffer(vkCtx: GPUContext)
	{
		stgBuffer?.let {
			it.close(vkCtx)
			stgBuffer = null
		}
	}

	private fun recordCopyBuffer(stack: MemoryStack, cmd: GPUCommandBuffer, bufferData: GPUBuffer)
	{
		val region = VkBufferImageCopy.calloc(1, stack)
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
			.imageExtent { it.width(width).height(height).depth(1) }

		vkCmdCopyBufferToImage(
			cmd.vkCommandBuffer,
			bufferData.buffer,
			image.vkImage,
			VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
			region,
		)
	}

	fun recordTextureTransition (cmd: GPUCommandBuffer)
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
				recordCopyBuffer(stack, cmd, staging)
				imageBarrier(
					stack, cmd.vkCommandBuffer, image.vkImage,
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

	private fun createStgBuffer (vkCtx: GPUContext, data: MemorySegment): GPUBuffer
	{
		val size = data.byteSize()
		val stgBuffer = GPUBuffer(
			vkCtx,
			size,
			VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
			(
				VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
				VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
			)
		)
		stgBuffer.doMapped(vkCtx) {
			val buffer = FUtil.createMemoryAt(it, stgBuffer.requestedSize)
			buffer.copyFrom(data)
		}
		return stgBuffer
	}

}