package fpw.ren.goobers

import org.joml.Matrix4f

class IdentityViewPoint: ViewPoint()
{
	override val viewMatrix = Matrix4f()
	override val projectionMatrix = Matrix4f()

	override fun updateMatricies()
	{
	}
}