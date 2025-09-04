package fpw.ren.goobers

import fpw.ren.goobers.Projection
import fpw.ren.goobers.RenderEntity
import org.joml.Matrix4f

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