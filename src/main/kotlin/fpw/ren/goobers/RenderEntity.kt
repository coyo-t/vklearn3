package fpw.ren.goobers

import fpw.ResourceLocation
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

open class RenderEntity
{
	val id: String
	var model: ResourceLocation? = null

	val modelMatrix = Matrix4f()
	val location = Vector3f()
	val rotation = Quaternionf()
	var scale = 1f

	var update: RenderEntityUpdateCallback? = null
//	var update: (RenderEntity.(dt:Long)->Unit)? = null

	constructor (id: String)
	{
		this.id = id
	}

	fun setUpdateCallback (f:RenderEntityUpdateCallback?)
	{
		update = f
	}

	fun updateModelMatrix()
	{
		modelMatrix.translationRotateScale(location, rotation, scale)
	}

}