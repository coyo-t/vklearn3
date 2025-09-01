package fpw.ren.gpu.queuez

import fpw.Renderer
import fpw.ren.gpu.getPresentQueueFamilyIndex

class PresentQueue (vkCtx: Renderer, queueIndex: Int):
	CommandQueue(vkCtx, vkCtx.getPresentQueueFamilyIndex(), queueIndex)