package com.catsofwar

import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWCharCallbackI
import org.lwjgl.glfw.GLFWKeyCallbackI

class KeyboardInput (val window: Window) : GLFWKeyCallbackI
{
	private val singlePressKeyMap = mutableMapOf<Int, Boolean>()
	private val callbacks = mutableListOf<GLFWKeyCallbackI>()

	init
	{
		GLFW.glfwSetKeyCallback(window.handle, this)
	}

	fun addKeyCallBack (callback: GLFWKeyCallbackI)
	{
		callbacks.add(callback)
	}

	fun input()
	{
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