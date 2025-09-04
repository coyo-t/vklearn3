package fpw.ren.device

import fpw.ren.Renderer

class GPUDevice (
	val renderer: Renderer,
	preferred: String?,
)
{

	val hardwareDevice = HardwareDevice.createPhysicalDevice(renderer.instance, preferred)
	val logicalDevice = LogicalDevice(hardwareDevice)

	fun waitIdle ()
	{
		logicalDevice.waitIdle()
	}

	fun free ()
	{
		logicalDevice.free()
		hardwareDevice.free()
	}
}