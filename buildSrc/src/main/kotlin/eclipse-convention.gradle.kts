import java.util.Locale

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
 * Maps the running OS and architecture to the SWT "osgi.platform" name, covering
 * aarch64/arm64 as well as x86_64 on every OS.
 */
fun runningOsgiPlatform(): String {
  val osName = System.getProperty("os.name").lowercase(Locale.ROOT)
  val osArch = System.getProperty("os.arch").lowercase(Locale.ROOT)
  val arch = when (osArch) {
    "aarch64", "arm64" -> "aarch64"
    "amd64", "x86_64", "x64" -> "x86_64"
    else -> osArch
  }
  return when {
    osName.contains("win") -> "win32.win32.$arch"
    osName.contains("mac") -> "cocoa.macosx.$arch"
    else -> "gtk.linux.$arch"
  }
}

/**
 * The org.eclipse.swt module depends on "org.eclipse.swt.${osgi.platform}".
 * Resolve that placeholder to the natives of the running platform, which is
 * necessary to build with the org.eclipse.swt module.
 */
val osgiPlatformPlaceholder = "\${osgi.platform}"
val osgiPlatform: String = runningOsgiPlatform()

configurations.configureEach {
  resolutionStrategy.eachDependency {
    val requestedName: String = requested.name
    if (requestedName.contains(osgiPlatformPlaceholder)) {
      val targetName = requestedName.replace(osgiPlatformPlaceholder, osgiPlatform)
      val requestedGroup: String? = requested.group
      val requestedVersion: String? = requested.version
      val notation = listOfNotNull(requestedGroup, targetName, requestedVersion).joinToString(":")
      useTarget(notation)
      because("resolve SWT natives for the running platform ($osgiPlatform)")
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
