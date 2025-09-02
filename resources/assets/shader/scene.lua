return {
	vertex = [[
		#version 450

		layout(location=0)
		in vec3 inPos;
		layout(location=1)
		in vec2 intTextCoords;

		layout(location=0)
		out vec2 outTextCoords;

		layout(set=0, binding=0)
		uniform MATRICES
		{
			mat4 projectionMatrix;
			mat4 modelMatrix;
		};

		void main ()
		{
			gl_Position = projectionMatrix * modelMatrix * vec4(inPos, 1);
			outTextCoords = intTextCoords;
		}
	]],
	fragment = [[
		#version 450

		layout(location=0)
		in vec2 inTextCoords;

		layout(location=0)
		out vec4 outFragColor;

		layout(set=0, binding=0)
		uniform sampler2D gm_BaseTexture;

		void main()
		{
			outFragColor = texture(gm_BaseTexture, inTextCoords);
		}
	]],
}