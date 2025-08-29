package fpw.ren.gpu

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRSurface
import org.lwjgl.vulkan.VK10

class PresentQueue (vkCtx: GPUContext, queueIndex: Int):
	GPUCommandQueue(vkCtx, getPresentQueueFamilyIndex(vkCtx), queueIndex)
{
	companion object
	{
		private fun getPresentQueueFamilyIndex (vkCtx: GPUContext): Int
		{
			var index = -1
			MemoryStack.stackPush().use { stack ->
				val queuePropsBuff = vkCtx.physDevice.vkQueueFamilyProps
				val numQueuesFamilies: Int = queuePropsBuff.capacity()
				val intBuff = stack.mallocInt(1)
				for (i in 0..<numQueuesFamilies)
				{
					KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(
						vkCtx.physDevice.vkPhysicalDevice,
						i, vkCtx.surface.vkSurface, intBuff
					)
					val supportsPresentation = intBuff.get(0) == VK10.VK_TRUE
					if (supportsPresentation)
					{
						index = i
						break
					}
				}
			}
			require (index >= 0) {
				"Failed to get Presentation Queue family index"
			}
			return index
		}
	}
}