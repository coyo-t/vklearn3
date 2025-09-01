package fpw

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f


open class RenderEntity
{
	val id: String
	var modelId: String? = null
	val modelMatrix = Matrix4f()
	val location = Vector3f()
	val rotation = Quaternionf()
	var scale = 1f

	var update: (RenderEntity.(dt:Long)->Unit)? = null

	constructor (id: String)
	{
		this.id = id
	}

	fun updateModelMatrix()
	{
		modelMatrix.translationRotateScale(location, rotation, scale)
	}

}