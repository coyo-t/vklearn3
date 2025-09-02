package fpw

import org.joml.Matrix4f

abstract class ViewPoint
{

	abstract val viewMatrix: Matrix4f
	abstract val projectionMatrix: Matrix4f

	abstract fun updateMatricies ()
}

class IdentityViewPoint: ViewPoint()
{
	override val viewMatrix = Matrix4f()
	override val projectionMatrix = Matrix4f()

	override fun updateMatricies()
	{
	}
}

class EntityViewPoint(
	var projection: Projection,
	var viewer: RenderEntity? = null,
): ViewPoint()
{
	override val viewMatrix = Matrix4f()
	override val projectionMatrix = Matrix4f()

	override fun updateMatricies()
	{
		viewer?.let {
			it.updateModelMatrix()
			viewMatrix.set(it.modelMatrix).invert()
		}
		projectionMatrix.set(projection.projectionMatrix)
	}
}
