package fpw.ren.gpu.queuez

import fpw.ren.gpu.GPUContext
import org.lwjgl.vulkan.VK10

class GraphicsQueue(vkCtx: GPUContext, queueIndex: Int):
	GPUCommandQueue(vkCtx, getGraphicsQueueFamilyIndex(vkCtx), queueIndex)
{
	companion object
	{
		private fun getGraphicsQueueFamilyIndex(vkCtx: GPUContext): Int
		{
			val queuePropsBuff = vkCtx.hardware.vkQueueFamilyProps
			val uhh = queuePropsBuff.indexOfFirst { (it.queueFlags() and VK10.VK_QUEUE_GRAPHICS_BIT) != 0 }
			require(uhh >= 0) {
				"Failed to get graphics Queue family index"
			}
			return uhh
		}
	}
}