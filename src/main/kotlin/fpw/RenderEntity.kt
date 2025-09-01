package fpw

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f


open class RenderEntity
{
	val id: String
	val modelId: String
	val modelMatrix = Matrix4f()
	val location = Vector3f()
	val rotation = Quaternionf()
	var scale = 1f

	var update: (RenderEntity.(dt:Long)->Unit)? = null

	constructor (id: String, modelId: String)
	{
		this.id = id
		this.modelId = modelId
	}

	fun updateModelMatrix()
	{
		modelMatrix.translationRotateScale(location, rotation, scale)
	}

}