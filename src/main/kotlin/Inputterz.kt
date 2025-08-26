package com.catsofwar

import org.joml.Vector2f
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWCharCallbackI
import org.lwjgl.glfw.GLFWKeyCallbackI

class Inputterz (val window: Window) : GLFWKeyCallbackI
{
	private val singlePressKeyMap = mutableMapOf<Int, Boolean>()
	private val callbacks = mutableListOf<GLFWKeyCallbackI>()

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
		GLFW.glfwSetKeyCallback(window.handle, this)

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

	fun addKeyCallBack (callback: GLFWKeyCallbackI)
	{
		callbacks.add(callback)
	}

	fun input()
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

	override fun invoke (handle: Long, keyCode: Int, scanCode: Int, action: Int, mods: Int)
	{
		singlePressKeyMap[keyCode] = action == GLFW.GLFW_PRESS
		callbacks.forEach { it.invoke(handle, keyCode, scanCode, action, mods) }
	}

	fun keyPressed(keyCode: Int): Boolean
	{
		return GLFW.glfwGetKey(window.handle, keyCode) == GLFW.GLFW_PRESS
	}

	fun keySinglePress(keyCode: Int): Boolean
	{
		return singlePressKeyMap[keyCode] == true
	}

	fun resetInput ()
	{
		singlePressKeyMap.clear()
	}

	fun setCharCallBack (charCallback: GLFWCharCallbackI?)
	{
		GLFW.glfwSetCharCallback(window.handle, charCallback)
	}
}