package fpw.ren.descriptor

import fpw.ren.GPUtil.gpuCheck
import fpw.ren.device.GPUDevice
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorPoolSize
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo

class DescriptorAllocatorGrowable(val device: GPUDevice)
{

	private val ratios = mutableListOf<PoolSizeRatio>()
	private val fullPools = mutableListOf<DescPool>()
	private val readyPools = mutableListOf<DescPool>()
	private var setsPerPool = 0

	fun init (maxSets: Int, poolRatios: List<PoolSizeRatio>)
	{
		ratios.clear()
		ratios.addAll(poolRatios)
		val newPool = createPool(maxSets, poolRatios)
		setsPerPool = (maxSets * 1.5).toInt()
		readyPools += newPool
	}

	fun clearPools ()
	{
		val dPtr = device.logicalDevice.vkDevice
		for (it in readyPools)
		{
			vkResetDescriptorPool(dPtr, it.vkDescPool, 0)
		}
		for (it in fullPools)
		{
			vkResetDescriptorPool(dPtr, it.vkDescPool, 0)
			readyPools += it
		}
		fullPools.clear()
	}

	fun destroyPools ()
	{
		val dPtr = device.logicalDevice.vkDevice
		for (p in readyPools)
		{
			vkDestroyDescriptorPool(dPtr, p.vkDescPool, null)
		}
		readyPools.clear()
		for (p in fullPools)
		{
			vkDestroyDescriptorPool(dPtr, p.vkDescPool, null)
		}
		fullPools.clear()
	}

	fun allocate (layout: DescriptorSetLayout, pNext:Long=0L): DescSet
	{
		// get or create a pool to allocate from
		MemoryStack.stackPush().use { stack ->
			var poolToUse = getPool()
			val allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
			allocInfo.pNext(pNext)
			allocInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
			allocInfo.descriptorPool(poolToUse.vkDescPool)
			allocInfo.pSetLayouts(stack.longs(layout.vkDescLayout))

//			VkDescriptorSet ds;
			val dsPtr = stack.mallocLong(1)
			val vkDevice = device.logicalDevice.vkDevice
			val result = vkAllocateDescriptorSets(vkDevice, allocInfo, dsPtr)

			//allocation failed. Try again
			if (result == VK_ERROR_OUT_OF_POOL_MEMORY || result == VK_ERROR_FRAGMENTED_POOL)
			{
				fullPools += poolToUse

				poolToUse = getPool()
				allocInfo.descriptorPool(poolToUse.vkDescPool)
				gpuCheck(
					vkAllocateDescriptorSets(vkDevice, allocInfo, dsPtr),
					"allocate deescriptor set"
				)
			}
			readyPools += poolToUse
			return DescSet(dsPtr[0])
		}

	}

	private fun getPool (): DescPool
	{
		val newPool = if (readyPools.isNotEmpty())
		{
			readyPools.removeLast()
		}
		else
		{
			val pevSet = setsPerPool
			setsPerPool = (setsPerPool * 1.5).toInt()
			if (setsPerPool > 4092)
			{
				setsPerPool = 4092
			}
			createPool(pevSet, ratios)
		}
		return newPool
	}

	private fun createPool (setCount:Int, ratios: List<PoolSizeRatio>): DescPool
	{
		MemoryStack.stackPush().use { stack ->
			val poolSizes = VkDescriptorPoolSize.calloc(ratios.size, stack)
			for (i in 0..<ratios.size)
			{
				val ratio = ratios[i]
				val it = poolSizes[i]
				it.type(ratio.type.vk)
				it.descriptorCount((ratio.ratio * setCount).toInt())
			}

			val poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
			poolInfo.`sType$Default`()
			poolInfo.flags(0)
			poolInfo.maxSets(setCount)
			poolInfo.pPoolSizes(poolSizes)

			val newPool = stack.mallocLong(1)
			gpuCheck(
				vkCreateDescriptorPool(device.logicalDevice.vkDevice, poolInfo, null, newPool),
				"desc. pool"
			)
			return DescPool(newPool[0])
		}
	}

	class DescSet (val vkDescSet: Long)

	class DescPool (val vkDescPool: Long)

	class PoolSizeRatio(
		val type: DescriptorType,
		val ratio: Float,
	)
	{
		constructor(type: DescriptorType, ratio: Number): this(type, ratio.toFloat())
	}

	// https://vkguide.dev/docs/new_chapter_4/descriptor_abstractions/
}