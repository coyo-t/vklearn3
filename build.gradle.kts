plugins {
	kotlin("jvm") version "2.2.0"
}


group = "com.catsofwar"
version = "1.0-SNAPSHOT"

repositories {
	mavenCentral()
}

val lwjglNatives = Pair(
	System.getProperty("os.name")!!,
	System.getProperty("os.arch")!!
).let { (name, arch) ->
	when {
		arrayOf("Linux", "SunOS", "Unit").any { name.startsWith(it) } -> "natives-linux"
		arrayOf("Windows").any { name.startsWith(it) } -> "natives-windows"
		else ->
			throw Error("Unrecognized or unsupported platform. Please set \"lwjglNatives\" manually")
	}
}

dependencies {
//	testImplementation(kotlin("test"))

	implementation(platform("org.lwjgl:lwjgl-bom:3.3.6"))
	implementation("org.lwjgl", "lwjgl")
	implementation("org.lwjgl", "lwjgl-assimp")
	implementation("org.lwjgl", "lwjgl-bgfx")
	implementation("org.lwjgl", "lwjgl-cuda")
	implementation("org.lwjgl", "lwjgl-egl")
	implementation("org.lwjgl", "lwjgl-freetype")
	implementation("org.lwjgl", "lwjgl-glfw")
	implementation("org.lwjgl", "lwjgl-jemalloc")
	implementation("org.lwjgl", "lwjgl-ktx")
	implementation("org.lwjgl", "lwjgl-libdivide")
	implementation("org.lwjgl", "lwjgl-lmdb")
	implementation("org.lwjgl", "lwjgl-lz4")
	implementation("org.lwjgl", "lwjgl-meow")
	implementation("org.lwjgl", "lwjgl-meshoptimizer")
	implementation("org.lwjgl", "lwjgl-msdfgen")
	implementation("org.lwjgl", "lwjgl-nanovg")
	implementation("org.lwjgl", "lwjgl-nfd")
	implementation("org.lwjgl", "lwjgl-nuklear")
	implementation("org.lwjgl", "lwjgl-openal")
	implementation("org.lwjgl", "lwjgl-opencl")
	implementation("org.lwjgl", "lwjgl-opengl")
	implementation("org.lwjgl", "lwjgl-openvr")
	implementation("org.lwjgl", "lwjgl-opus")
	implementation("org.lwjgl", "lwjgl-par")
	implementation("org.lwjgl", "lwjgl-remotery")
	implementation("org.lwjgl", "lwjgl-rpmalloc")
	implementation("org.lwjgl", "lwjgl-shaderc")
	implementation("org.lwjgl", "lwjgl-spvc")
	implementation("org.lwjgl", "lwjgl-sse")
	implementation("org.lwjgl", "lwjgl-stb")
	implementation("org.lwjgl", "lwjgl-tinyexr")
	implementation("org.lwjgl", "lwjgl-tinyfd")
	implementation("org.lwjgl", "lwjgl-tootle")
	implementation("org.lwjgl", "lwjgl-vma")
	implementation("org.lwjgl", "lwjgl-vulkan")
	implementation("org.lwjgl", "lwjgl-xxhash")
	implementation("org.lwjgl", "lwjgl-zstd")
	implementation("org.lwjgl", "lwjgl", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-assimp", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-bgfx", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-freetype", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-jemalloc", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-ktx", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-libdivide", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-lmdb", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-lz4", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-meow", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-meshoptimizer", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-msdfgen", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-nanovg", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-nfd", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-nuklear", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-openal", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-opengl", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-openvr", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-opus", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-par", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-remotery", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-rpmalloc", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-shaderc", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-spvc", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-sse", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-stb", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-tinyexr", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-tinyfd", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-tootle", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-vma", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-xxhash", classifier = lwjglNatives)
	implementation("org.lwjgl", "lwjgl-zstd", classifier = lwjglNatives)
	implementation("org.joml", "joml-primitives", "1.10.0")
	implementation("com.code-disaster.steamworks4j", "steamworks4j", "1.9.0")
	implementation("com.code-disaster.steamworks4j", "steamworks4j-server", "1.9.0")
	implementation("org.joml", "joml", "1.10.8")


	implementation("party.iroiro.luajava:luajava:4.0.2")
	implementation("party.iroiro.luajava:lua54:4.0.2")
	implementation("party.iroiro.luajava:lua54-platform:4.0.2:natives-desktop")

	implementation("org.tinylog:tinylog-api-kotlin:2.7.0")
	implementation("org.tinylog:tinylog-impl:2.7.0")

//	provided "party.iroiro.luajava:luajava:4.0.2"
//	provided 'party.iroiro.luajava:lua54:4.0.2'
//	provided 'party.iroiro.luajava:lua54-platform:4.0.2:natives-desktop'
}

//tasks.test {
//	useJUnitPlatform()
//}
kotlin {
	jvmToolchain(24)
}

