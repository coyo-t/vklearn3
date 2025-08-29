package fpw.ren.gpu

class PushConstantRange(
	val stage:Int,
	val offset:Int,
	val size:Int,
)
{

	constructor (stage:Int, range: ClosedRange<Int>):
		this(
			stage = stage,
			offset = range.start,
			size = range.endInclusive-range.start+1
		)

}