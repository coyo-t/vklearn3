package fpw.ren.descriptor

import fpw.ren.GPUBuffer
import fpw.ren.device.GPUDevice
import fpw.ren.image.ImageLayout
import fpw.ren.image.ImageView
import fpw.ren.texture.Sampler
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDescriptorBufferInfo
import org.lwjgl.vulkan.VkDescriptorImageInfo
import org.lwjgl.vulkan.VkWriteDescriptorSet


class DescWriter(val stack: MemoryStack)
{
	val writes = mutableListOf<VkWriteDescriptorSet>()

	fun writeImage (
		binding: Int,
		image: ImageView,
		sampler: Sampler,
		layout: ImageLayout,
		type: DescType
	)
	{
		val info = VkDescriptorImageInfo.calloc(1, stack)
		info.sampler(sampler.vkSampler)
		info.imageView(image.vkImageView)
		info.imageLayout(layout.vk)

		val write = VkWriteDescriptorSet.calloc(stack)
		write.sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
		write.dstBinding(binding)
		//left empty for now until we need to write it
		write.dstSet(VK_NULL_HANDLE)
		write.descriptorCount(1)
		write.descriptorType(type.vk)
		write.pImageInfo(info)
		writes += write
	}

	fun writeBuffer (
		binding: Int,
		buffer: GPUBuffer,
		size: Long,
		offset: Long,
		type: DescType
	)
	{
		check(type.validForBuffer) {
			"not a valid buffer desc. type"
		}
		val info = VkDescriptorBufferInfo.calloc(1, stack)
		info.buffer(buffer.bufferStruct)
		info.offset(offset)
		info.range(size)

		val write = VkWriteDescriptorSet.calloc(stack)
		write.sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
		write.dstBinding(binding)
		//left empty for now until we need to write it
		write.dstSet(VK_NULL_HANDLE)
		write.descriptorCount(1)
		write.descriptorType(type.vk)
		write.pBufferInfo(info)
		writes += write
	}

	fun clear ()
	{
		writes.clear()
	}


	fun updateSet (device: GPUDevice, descSet: DescSet)
	{
		for (write in writes)
		{
			write.dstSet(descSet.vkDescSet)
		}

		MemoryStack.stackPush().use { stack ->
			val temp = VkWriteDescriptorSet.malloc(writes.size, stack)

			// kind of silly
			for (i in 0..<writes.size)
			{
				temp.put(i, writes[i])
			}

			vkUpdateDescriptorSets(
				device.logicalDevice.vkDevice,
				temp,
				null,
			)
		}
	}

}