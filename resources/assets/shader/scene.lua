return {
	vertex = [[
		#version 450

		layout(location=0) in vec3 inPos;

		void main ()
		{
			gl_Position = vec4(inPos, 1);
		}
	]],
	fragment = [[
		#version 450

		layout(location=0) out vec4 final_pixel;

		void main ()
		{
			final_pixel = vec4(1, 0, 0, 1);
		}
	]],
}