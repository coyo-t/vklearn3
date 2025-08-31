package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDescriptorBufferInfo
import org.lwjgl.vulkan.VkDescriptorImageInfo
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo
import org.lwjgl.vulkan.VkWriteDescriptorSet


// shader sockets
class DescriptorSet
{
	val vkDescriptorSet: Long

	constructor (device: LogicalDevice, descPool: DescriptorSetPool, descSetLayout: DescriptorSetLayout)
	{
		MemoryStack.stackPush().use { stack ->
			val pDescriptorSetLayout = stack.mallocLong(1)
			pDescriptorSetLayout.put(0, descSetLayout.vkDescLayout)
			val allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
				.`sType$Default`()
				.descriptorPool(descPool.vkDescPool)
				.pSetLayouts(pDescriptorSetLayout)

			val pDescriptorSet = stack.mallocLong(1)
			vkCheck(
				vkAllocateDescriptorSets(device.vkDevice, allocInfo, pDescriptorSet),
				"Failed to create descriptor set"
			)
			vkDescriptorSet = pDescriptorSet.get(0)
		}
	}

	fun setBuffer(device: LogicalDevice, buffer: GPUBuffer, range: Long, binding: Int, type: Int)
	{
		MemoryStack.stackPush().use { stack ->
			val bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
				.buffer(buffer.bufferStruct)
				.offset(0)
				.range(range)
			val descrBuffer = VkWriteDescriptorSet.calloc(1, stack)

			descrBuffer.get(0)
				.`sType$Default`()
				.dstSet(vkDescriptorSet)
				.dstBinding(binding)
				.descriptorType(type)
				.descriptorCount(1)
				.pBufferInfo(bufferInfo)
			vkUpdateDescriptorSets(device.vkDevice, descrBuffer, null)
		}
	}

	fun setImages (
		device: LogicalDevice,
		textureSampler: Sampler,
		baseBinding: Int,
		vararg imgViews: ImageView)
	{
		if (imgViews.isEmpty())
			return
		MemoryStack.stackPush().use { stack ->
			val numImages = imgViews.size
			val descrBuffer = VkWriteDescriptorSet.calloc(numImages, stack)
			for (i in 0..<numImages)
			{
				val iv = imgViews[i]
				val imageInfo = VkDescriptorImageInfo.calloc(1, stack)
					.imageView(iv.vkImageView)
					.sampler(textureSampler.vkSampler)

				val layout = if (iv.isDepthImage)
					VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
				else
					VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
				imageInfo.imageLayout(layout)

				descrBuffer.get(i)
					.`sType$Default`()
					.dstSet(vkDescriptorSet)
					.dstBinding(baseBinding + i)
					.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
					.descriptorCount(1)
					.pImageInfo(imageInfo)
			}
			vkUpdateDescriptorSets(device.vkDevice, descrBuffer, null)
		}
	}

	fun setImagesArray(
		device: LogicalDevice,
		textureSampler: Sampler,
		baseBinding: Int,
		vararg imgViews: ImageView,
	)
	{
		MemoryStack.stackPush().use { stack ->
			val numImages = imgViews.size
			val imageInfos = VkDescriptorImageInfo.calloc(numImages, stack)
			for (i in 0..<numImages)
			{
				val iv = imgViews[i]
				val imageInfo = imageInfos.get(i)
				imageInfo
					.imageView(iv.vkImageView)
					.sampler(textureSampler.vkSampler)

				if (iv.isDepthImage)
				{
					imageInfo.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
				}
				else
				{
					imageInfo.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
				}
			}

			val descrBuffer = VkWriteDescriptorSet.calloc(1, stack)
			descrBuffer.get(0)
				.`sType$Default`()
				.dstSet(vkDescriptorSet)
				.dstBinding(baseBinding)
				.dstArrayElement(0)
				.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
				.descriptorCount(numImages)
				.pImageInfo(imageInfos)
			vkUpdateDescriptorSets(device.vkDevice, descrBuffer, null)
		}
	}
}