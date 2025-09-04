package fpw.ren.descriptor

import org.lwjgl.vulkan.VK10.*

enum class DescType (
	val vk: Int,
	val validForBuffer: Boolean
)
{
	Non (0, false),

	Sampler(
		VK_DESCRIPTOR_TYPE_SAMPLER,
		false,
	),
	CombinedImageSampler(
		VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
		false,
	),
	SampledImage(
		VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
		false,
	),
	StorageImage(
		VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
		false,
	),
	UniformTexelBuffer(
		VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER,
		false,
	),
	StorageTexelBuffer(
		VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER,
		false,
	),
	UniformBuffer(
		VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
		true,
	),
	StorageBuffer(
		VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
		true,
	),
	DynamicUniformBuffer(
		VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC,
		true,
	),
	DynamicStorageBuffer(
		VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC,
		true,
	),
	InputAttachment(
		VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT,
		false,
	),
}