package fpw.ren.gpu

import fpw.ren.gpu.GPUtil.gpuCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.vma.Vma.vmaCreateAllocator
import org.lwjgl.util.vma.Vma.vmaDestroyAllocator
import org.lwjgl.util.vma.VmaAllocatorCreateInfo
import org.lwjgl.util.vma.VmaVulkanFunctions
import org.lwjgl.vulkan.VK14.*


class MemAlloc
{
	val vmaAlloc: Long

	constructor (instance: GPUInstance, physDevice: HardwareDevice, device: LogicalDevice)
	{
		MemoryStack.stackPush().use { stack ->
			val pAllocator = stack.mallocPointer(1)
			val vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack)
				.set(instance.vkInstance, device.vkDevice)

			val createInfo = VmaAllocatorCreateInfo.calloc(stack)
				.instance(instance.vkInstance)
				.vulkanApiVersion(VK_API_VERSION_1_3)
				.device(device.vkDevice)
				.physicalDevice(physDevice.vkPhysicalDevice)
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