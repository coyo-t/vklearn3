package fpw

import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported
import org.lwjgl.system.MemoryUtil


class Window private constructor(
	val handle: Long,
	wide:Int,
	tall:Int,
): AutoCloseable, DimensionsProvider
{
	override var wide = wide
		private set
	override var tall = tall
		private set

	lateinit var input: Inputterz
		private set

	companion object
	{
		fun create (title: String, initialWide:Int, initialTall: Int): Window
		{
			check(glfwInit()) {
				"Unable to initialize GLFW"
			}
			check(glfwVulkanSupported()) {
				"Cannot find a compatible Vulkan installable client driver (ICD)"
			}

			val vidMode = requireNotNull(glfwGetVideoMode(glfwGetPrimaryMonitor())) {
				"Error getting primary monitor"
			}
			//			width = vidMode.width()
//			height = vidMode.height()

			glfwDefaultWindowHints()
			glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
			glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE)


			// Create the window
			val handle = glfwCreateWindow(initialWide, initialTall, title, MemoryUtil.NULL, MemoryUtil.NULL)
			if (handle == MemoryUtil.NULL)
			{
				throw RuntimeException("Failed to create the GLFW window")
			}
			val outs = Window(handle, initialWide, initialTall)

			glfwSetFramebufferSizeCallback(handle) { _, w, h ->
				outs.wide = w
				outs.tall = h
			}

			outs.input = Inputterz(outs)

			return outs
		}
	}

	override fun close ()
	{
		glfwFreeCallbacks(handle)
		glfwDestroyWindow(handle)
		glfwTerminate()
	}

	fun pollEvents()
	{
		input.input()
	}

	fun resetInput()
	{
		input.resetInput()
	}

	var shouldClose: Boolean
		get() = glfwWindowShouldClose(handle)
		set(v) { glfwSetWindowShouldClose(handle, v) }

}