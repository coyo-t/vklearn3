package fpw.ren.descriptor

import fpw.ren.GPUBuffer
import fpw.ren.GPUtil
import fpw.ren.descriptor.DescriptorAllocatorGrowable.DescSet
import fpw.ren.device.GPUDevice
import fpw.ren.image.ImageLayout
import fpw.ren.image.ImageView
import fpw.ren.texture.Sampler
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDescriptorBufferInfo
import org.lwjgl.vulkan.VkDescriptorImageInfo
import org.lwjgl.vulkan.VkWriteDescriptorSet


class DescWriter
{
	val imageInfos = mutableListOf<VkDescriptorImageInfo>()
	val bufferInfos = mutableListOf<VkDescriptorBufferInfo>()
	val writes = mutableListOf<VkWriteDescriptorSet>()

	fun writeImage (
		binding: Int,
		image: ImageView,
		sampler: Sampler,
		layout: ImageLayout,
		type: DescriptorType
	)
	{
		val info = GPUtil.registerForCleanup(VkDescriptorImageInfo.calloc(1))
		info.sampler(sampler.vkSampler)
		info.imageView(image.vkImage)
		info.imageLayout(layout.vk)
		imageInfos += info

		val write = GPUtil.registerForCleanup(VkWriteDescriptorSet.calloc())
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
		type: DescriptorType
	)
	{
		check(type.validForBuffer) {
			"not a valid buffer desc. type"
		}
		val info = GPUtil.registerForCleanup(VkDescriptorBufferInfo.calloc(1))
		info.buffer(buffer.bufferStruct)
		info.offset(offset)
		info.range(size)
		bufferInfos += info

		val write = GPUtil.registerForCleanup(VkWriteDescriptorSet.calloc())
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
		imageInfos.clear()
		bufferInfos.clear()
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