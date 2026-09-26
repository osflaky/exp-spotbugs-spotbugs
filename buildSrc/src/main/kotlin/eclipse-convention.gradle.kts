plugins {
  id ("com.diffplug.eclipse.mavencentral")
}

val pdeTool = configurations.create("pdeTool") {
  isTransitive = false
}

eclipseMavenCentral {
  silenceEquoIDE()
  release("4.33.0") {
    compileOnly("org.eclipse.ant.core")
    compileOnly("org.eclipse.core.resources")
    compileOnly("org.eclipse.core.runtime")
    compileOnly("org.eclipse.jdt.core")
    compileOnly("org.eclipse.jdt.ui")
    compileOnly("org.eclipse.jface")
    compileOnly("org.eclipse.pde")
    compileOnly("org.eclipse.ui.workbench")
    testImplementation("org.eclipse.core.runtime")

    dep("pdeTool", "org.eclipse.pde.build")

    // TODO these packages are not listed in the manifest
    compileOnly("org.eclipse.pde.ui")
    compileOnly("org.eclipse.swt")

    constrainTransitivesToThisRelease()
  }
}

/**
 * Resolve the SWT native fragment (org.eclipse.swt.${osgi.platform}) for the running platform.
 * Goomph's useNativesForRunningPlatform() does not know every os/arch combination
 * (e.g. Linux on aarch64), so compute the ws.os.arch triple here.
 */
fun swtPlatform(): String {
  val osName = System.getProperty("os.name").lowercase()
  val osArch = System.getProperty("os.arch").lowercase()
  val arch = when (osArch) {
    "aarch64", "arm64" -> "aarch64"
    "ppc64le" -> "ppc64le"
    "riscv64" -> "riscv64"
    "loongarch64" -> "loongarch64"
    else -> "x86_64"
  }
  return when {
    osName.contains("win") -> "win32.win32.$arch"
    osName.contains("mac") -> "cocoa.macosx.$arch"
    else -> "gtk.linux.$arch"
  }
}

val swtPlatformName = swtPlatform()
configurations.all {
  resolutionStrategy.eachDependency {
    if (requested.name.contains("\${osgi.platform}")) {
      val name = requested.name.replace("\${osgi.platform}", swtPlatformName)
      val version = requested.version
      useTarget(if (version.isNullOrEmpty()) "${requested.group}:$name" else "${requested.group}:$name:$version")
    }
  }
}

/**
 * Unzip "org.eclipse.pde.build" package into the outputDir.
 */
val pdeToolDir = layout.buildDirectory.dir("pdeTool")
val unzipPdeTool = tasks.register<Copy>("unzipPdeTool") {
  from(zipTree(pdeTool.singleFile))
  into(pdeToolDir)
}

dependencies {
  compileOnly(files(pdeToolDir.map { dir: Directory -> dir.file("pdebuild.jar") }) {
    builtBy(unzipPdeTool)
  })
}
