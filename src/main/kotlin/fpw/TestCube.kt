package fpw

import fpw.ren.model.VertexFormatBuilder.Companion.buildVertexFormat

object TestCube
{
	val format = buildVertexFormat {
		location3D()
		texcoord2D()
	}
}