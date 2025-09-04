package fpw.ren.goobers

import org.joml.Matrix4f

abstract class ViewPoint
{

	abstract val viewMatrix: Matrix4f
	abstract val projectionMatrix: Matrix4f

	abstract fun updateMatricies ()
}