package fpw

class Scene (val window: Window)
{
	val entities = mutableListOf<Entity>()
	val projection = Projection(
		fov = 90f,
		zNear = 0.001f,
		zFar = 100f,
		width = window.wide,
		height = window.tall,
	)

}
