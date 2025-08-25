package com.catsofwar

import org.joml.Vector2f
import org.lwjgl.glfw.GLFW

class MouseInput (val window: Window)
{
	val currentPos = Vector2f()
	val deltaPos = Vector2f()
	private val previousPos = Vector2f(-1f)

	private var inWindow = false

	var isLeftButtonPressed = false
		private set

	var isRightButtonPressed = false
		private set

	init
	{
		GLFW.glfwSetCursorPosCallback(window.handle) { handle, xpos, ypos ->
			currentPos.x = xpos.toFloat()
			currentPos.y = ypos.toFloat()
		}
		GLFW.glfwSetCursorEnterCallback(window.handle) { handle, entered ->
			inWindow = entered
		}
		GLFW.glfwSetMouseButtonCallback(window.handle) { handle, button, action, mode ->
			this.isLeftButtonPressed = button == GLFW.GLFW_MOUSE_BUTTON_1 && action == GLFW.GLFW_PRESS
			this.isRightButtonPressed = button == GLFW.GLFW_MOUSE_BUTTON_2 && action == GLFW.GLFW_PRESS
		}
	}

	fun input ()
	{
		deltaPos.x = 0f
		deltaPos.y = 0f
		if (previousPos.x >= 0 && previousPos.y >= 0 && inWindow)
		{
			deltaPos.x = currentPos.x - previousPos.x
			deltaPos.y = currentPos.y - previousPos.y
		}
		previousPos.x = currentPos.x
		previousPos.y = currentPos.y
	}
}