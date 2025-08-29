package fpw

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f


class Entity
{
	val id: String
	val modelId: String
	val modelMatrix = Matrix4f()
	val position = Vector3f()
	val rotation = Quaternionf()
	var scale = 1f

	constructor (id: String, modelId: String, x:Float, y:Float, z:Float)
	{
		this.id = id
		this.modelId = modelId
		this.position.set(x,y,z)
		updateModelMatrix()
	}
	fun resetRotation()
	{
		rotation.x = 0f
		rotation.y = 0f
		rotation.z = 0f
		rotation.w = 1f
	}

	fun updateModelMatrix()
	{
		modelMatrix.translationRotateScale(position, rotation, scale)
	}

}