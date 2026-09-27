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

// necessary to build with the org.eclipse.swt module: org.eclipse.swt depends on
// "org.eclipse.swt.${osgi.platform}", so the placeholder is resolved here to the SWT
// fragment matching the running OS and CPU architecture (including aarch64 on Linux,
// which the generic native detection maps to the wrong fragment)
val osgiPlatformPlaceholder = "\${osgi.platform}"

val swtOsName: String = System.getProperty("os.name").lowercase()

val swtArch: String = when (System.getProperty("os.arch").lowercase()) {
  "aarch64", "arm64" -> "aarch64"
  "ppc64le" -> "ppc64le"
  "riscv64" -> "riscv64"
  "loongarch64" -> "loongarch64"
  else -> "x86_64"
}

val swtPlatform: String = when {
  swtOsName.contains("win") -> "win32.win32.$swtArch"
  swtOsName.contains("mac") -> "cocoa.macosx.$swtArch"
  else -> "gtk.linux.$swtArch"
}

configurations.configureEach {
  resolutionStrategy.eachDependency {
    val requestedName: String = requested.name
    if (requestedName.contains(osgiPlatformPlaceholder)) {
      val nativeName = requestedName.replace(osgiPlatformPlaceholder, swtPlatform)
      useTarget(requested.group + ":" + nativeName + ":" + requested.version)
      because("resolve SWT native fragment for the running platform " + swtPlatform)
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
