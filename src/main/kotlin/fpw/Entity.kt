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

	var update: (Entity.(dt:Long)->Unit)? = null

	constructor (id: String, modelId: String, x:Float, y:Float, z:Float)
	{
		this.id = id
		this.modelId = modelId
		this.position.set(x,y,z)
		updateModelMatrix()
	}

	fun updateModelMatrix()
	{
		modelMatrix.translationRotateScale(position, rotation, scale)
	}

}