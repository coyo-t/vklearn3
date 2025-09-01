package fpw.ren.gpu.queuez

import fpw.Renderer
import fpw.ren.gpu.getGraphicsQueueFamilyIndex

class GraphicsQueue(vkCtx: Renderer, queueIndex: Int):
	CommandQueue(vkCtx, vkCtx.getGraphicsQueueFamilyIndex(), queueIndex)
