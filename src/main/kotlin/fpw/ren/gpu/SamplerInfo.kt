package fpw.ren.gpu

data class SamplerInfo(
	val addressMode: Int,
	val borderColor: Int,
	val mipLevels: Int,
	val anisotropy: Boolean
)