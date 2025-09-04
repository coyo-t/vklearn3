package fpw

import fpw.FUtil.requireHandleNotNullptr
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL


class Window private constructor(
	val handle: Long,
	wide:Int,
	tall:Int,
)
{
	var wide = wide
		private set
	var tall = tall
		private set

	lateinit var input: Inputterz
		private set

	var time: Double
		get() = glfwGetTime()
		set(v) = glfwSetTime(v)

	fun show ()
	{
		glfwShowWindow(handle)
	}

	companion object
	{
		private var ALL_WINDOWZ = mutableSetOf<Long>()
		fun create (title: String, initWide:Int, initTall: Int): Window
		{
			if (ALL_WINDOWZ.isEmpty())
			{
				check(glfwInit()) {
					"Unable to initialize GLFW"
				}
				check(glfwVulkanSupported()) {
					"Cannot find a compatible Vulkan installable client driver (ICD)"
				}
			}


			glfwDefaultWindowHints()
			glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
			glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE)

			val vidMode = requireNotNull(glfwGetVideoMode(glfwGetPrimaryMonitor())) {
				"Error getting primary monitor"
			}
			val vidWide = vidMode.width()
			val vidTall = vidMode.height()

			glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
			glfwWindowHint(
				GLFW_POSITION_X,
				(vidWide - initWide) shr 1,
			)
			glfwWindowHint(
				GLFW_POSITION_Y,
				(vidTall - initTall) shr 1,
			)
			// Create the window
			val handle = requireHandleNotNullptr(glfwCreateWindow(initWide, initTall, title, NULL, NULL)) {
				"Failed to create the GLFW window"
			}
			ALL_WINDOWZ += handle
			val outs = Window(handle, initWide, initTall)

			glfwSetFramebufferSizeCallback(handle) { _, w, h ->
				outs.wide = w
				outs.tall = h
			}

			outs.input = Inputterz(outs)

			return outs
		}
	}

	fun free ()
	{
		ALL_WINDOWZ.remove(handle)
		glfwFreeCallbacks(handle)
		glfwDestroyWindow(handle)
		if (ALL_WINDOWZ.isEmpty())
		{
			glfwTerminate()
		}
	}

	var shouldClose: Boolean
		get() = glfwWindowShouldClose(handle)
		set(v) { glfwSetWindowShouldClose(handle, v) }

}