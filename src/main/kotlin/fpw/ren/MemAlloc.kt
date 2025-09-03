package fpw.ren

import fpw.ren.GPUtil.gpuCheck
import fpw.ren.device.GPUDevice
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.vma.Vma.vmaCreateAllocator
import org.lwjgl.util.vma.Vma.vmaDestroyAllocator
import org.lwjgl.util.vma.VmaAllocatorCreateInfo
import org.lwjgl.util.vma.VmaVulkanFunctions
import org.lwjgl.vulkan.VK14.*


class MemAlloc
{
	val vmaAlloc: Long

	constructor (instance: GPUInstance, gpu: GPUDevice)
	{
		MemoryStack.stackPush().use { stack ->
			val pAllocator = stack.mallocPointer(1)
			val vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack)
				.set(instance.vkInstance, gpu.logicalDevice.vkDevice)

			val createInfo = VmaAllocatorCreateInfo.calloc(stack)
				.instance(instance.vkInstance)
				.vulkanApiVersion(instance.apiVersion)
				.device(gpu.logicalDevice.vkDevice)
				.physicalDevice(gpu.hardwareDevice.vkPhysicalDevice)
				.pVulkanFunctions(vmaVulkanFunctions)
			gpuCheck(
				vmaCreateAllocator(createInfo, pAllocator),
				"Failed to create VMA allocator"
			)
			vmaAlloc = pAllocator.get(0)
		}
	}

	fun free ()
	{
		vmaDestroyAllocator(vmaAlloc)
	}
}