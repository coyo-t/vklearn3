package fpw

import org.joml.Matrix4f



class Projection
{
	val projectionMatrix = Matrix4f()

	var fov = 0f
	var zNear = 0f
	var zFar = 0f

	constructor (fov: Float, zNear: Float, zFar: Float, width: Int, height: Int)
	{
		this.fov = fov
		this.zNear = zNear
		this.zFar = zFar
		resize(width, height)
	}

	fun resize(width: Int, height: Int)
	{
		projectionMatrix.setPerspective(fov, width.toFloat() / height.toFloat(), zNear, zFar, true)
	}

}