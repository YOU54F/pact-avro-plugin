import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.sbt.packager.Keys.*
import sbt.Keys.*
import sbt.{Def, *}

object PublishSettings {

  val universalStageDir = settingKey[File]("Universal staging directory")
  val root = project in file("..")

  lazy val publishSettings: Seq[Def.Setting[?]] =
    Seq(
      executableScriptName := "pact-avro-plugin",
      Compile / doc / sources := Seq.empty,
      Compile / packageDoc / mappings := Seq.empty,
      Compile / resourceGenerators += Def.task {
        val artifactDir: File = target.value / "artifacts"
        Seq(
          generatePactPluginJson(artifactDir, version.value),
          generateInstallPluginSh(artifactDir, version.value),
          generatePs1Wrapper(sourceDirectory.value / "main" / "bin", version.value)
        )
      },
      Universal / mappings ++= Seq(
        sourceDirectory.value / "main" / "resources" / "logback.xml" -> "conf/logback.xml",
        sourceDirectory.value / "main" / "bin" / "pact-avro-plugin.ps1" -> "bin/pact-avro-plugin.ps1"
      ),
      Universal / javaOptions ++= Seq(
        "-Dfile.encoding=UTF-8",
        "-Dlogback.configurationFile=conf/logback.xml"
      ),
      Universal / packageName := s"pact-avro-plugin",
      Universal / topLevelDirectory := None
    )

  private def generatePactPluginJson(artifactDir: sbt.File, version: String): sbt.File = {
    val file = artifactDir / "pact-plugin.json"
    IO.write(file, PactPluginJson.json(version))
    file
  }

  private def generateInstallPluginSh(artifactDir: sbt.File, version: String): sbt.File = {
    val file = artifactDir / "install-plugin.sh"
    val content = """#!/bin/sh
      |
      |set -e
      |
      |VERSION="VERSION_HERE"
      |
      |case "$(uname -s)" in
      |
      |   Darwin|Linux|CYGWIN*|MINGW32*|MSYS*|MINGW*)
      |     echo '== Installing plugin =='
      |     mkdir -p ~/.pact/plugins/avro-${VERSION}
      |     wget --no-verbose https://github.com/austek/pact-avro-plugin/releases/download/v${VERSION}/pact-plugin.json -O ~/.pact/plugins/avro-${VERSION}/pact-plugin.json
      |     wget --no-verbose -c https://github.com/austek/pact-avro-plugin/releases/download/v${VERSION}/pact-avro-plugin.tgz \
      |     -O - | tar -xz -C ~/.pact/plugins/avro-${VERSION}
      |     ;;
      |
      |   *)
      |     echo "ERROR: $(uname -s) is not a supported operating system"
      |     exit 1
      |     ;;
      |esac""".stripMargin.replaceAll("VERSION_HERE", version)
    IO.write(file, content)
    file
  }

  private def generatePs1Wrapper(binDir: sbt.File, version: String): sbt.File = {
    val file = binDir / "pact-avro-plugin.ps1"
    val content = """# pact-avro-plugin launcher script
      |# Environment:
      |# JAVA_HOME - location of a JDK home dir (optional if java on path)
      |# CFG_OPTS  - JVM options (optional)
      |# Configuration:
      |# PACT_AVRO_PLUGIN_config.txt found in the PACT_AVRO_PLUGIN_HOME.
      |
      |$PACT_AVRO_PLUGIN_HOME = $null
      |$PACT_PLUGIN_VERSION = "VERSION_HERE"
      |$APP_HOME = $null
      |$APP_LIB_DIR = $null
      |$CFG_FILE = $null
      |$CFG_OPTS = $null
      |$JAVA_OPTS = $null
      |$JAVAOK = $null
      |$JAVAINSTALLED = $null
      |$BUNDLED_JVM = $null
      |$JAVACMD = $null
      |$SCRIPT_CONF_FILE = $null
      |$SCRIPT_CONF_ARGS = $null
      |$CUSTOM_MAIN_CLASS = $null
      |$MAIN_CLASS = $null
      |
      |$PACT_AVRO_PLUGIN_HOME = $env:PACT_AVRO_PLUGIN_HOME
      |if ([string]::IsNullOrEmpty($PACT_AVRO_PLUGIN_HOME)) {
      |    $APP_HOME = Resolve-Path -Path (Join-Path -Path (Split-Path -Parent $MyInvocation.MyCommand.Path) -ChildPath "..")
      |
      |    # Also set the old env name for backwards compatibility
      |    $PACT_AVRO_PLUGIN_HOME = Resolve-Path -Path (Join-Path -Path (Split-Path -Parent $MyInvocation.MyCommand.Path) -ChildPath "..")
      |} else {
      |    $APP_HOME = $PACT_AVRO_PLUGIN_HOME
      |}
      |
      |$APP_LIB_DIR = Join-Path $APP_HOME "lib"
      |
      |# Detect if we were double clicked, although theoretically A user could manually run cmd /c
      |$DOUBLECLICKED = $false
      |if ($MyInvocation.InvocationName -eq "cmd.exe" -and $MyInvocation.BoundParameters.ContainsKey("c")) {
      |    $DOUBLECLICKED = $true
      |}
      |
      |# Load the config file of extra options.
      |$CFG_FILE = Join-Path $APP_HOME "PACT_AVRO_PLUGIN_config.txt"
      |$CFG_OPTS = Get-Content -Path $CFG_FILE -Raw -ErrorAction SilentlyContinue
      |
      |# Use the value of the JAVA_OPTS environment variable if defined, rather than the config.
      |$JAVA_OPTS = $env:JAVA_OPTS
      |if ([string]::IsNullOrEmpty($JAVA_OPTS)) {
      |    $JAVA_OPTS = $CFG_OPTS
      |}
      |
      |# We keep in $JAVA_PARAMS all -J-prefixed and -D-prefixed arguments
      |# "-J" is stripped, "-D" is left as is, and everything is appended to JAVA_OPTS
      |$JAVA_PARAMS = @()
      |$APP_ARGS = @()
      |
      |$APP_CLASSPATH = $null
      |$APP_MAIN_CLASS = "$($APP_LIB_DIR)\io.pact.plugin-$PACT_PLUGIN_VERSION-launcher.jar"
      |$SCRIPT_CONF_FILE = Join-Path $APP_HOME "conf\application.ini"
      |
      |# Bundled JRE has priority over standard environment variables
      |if ($env:BUNDLED_JVM) {
      |    $JAVACMD = Join-Path $env:BUNDLED_JVM "bin\java.exe"
      |} else {
      |    if ($env:JAVACMD) {
      |        $JAVACMD = $env:JAVACMD
      |    } else {
      |        if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
      |            $JAVACMD = Join-Path $env:JAVA_HOME "bin\java.exe"
      |        }
      |    }
      |}
      |
      |if ([string]::IsNullOrEmpty($JAVACMD)) {
      |    $JAVACMD = "java"
      |}
      |
      |# Detect if this java is ok to use.
      |$JAVAINSTALLED = $false
      |$JAVACMDOutput = & $JAVACMD -version 2>&1
      |if ($JAVACMDOutput -match "java|openjdk") {
      |    $JAVAINSTALLED = $true
      |}
      |
      |$JAVAOK = $true
      |if (-not $JAVAINSTALLED) {
      |    Write-Host ""
      |    Write-Host "A Java JDK is not installed or can't be found."
      |    if ($env:JAVA_HOME) {
      |        Write-Host "JAVA_HOME = $env:JAVA_HOME"
      |    }
      |    Write-Host ""
      |    Write-Host "Please go to"
      |    Write-Host "  http://www.oracle.com/technetwork/java/javase/downloads/index.html"
      |    Write-Host "and download a valid Java JDK and install before running pact-avro-plugin."
      |    Write-Host ""
      |    Write-Host "If you think this message is in error, please check"
      |    Write-Host "your environment variables to see if ""java.exe"" and ""javac.exe"" are"
      |    Write-Host "available via JAVA_HOME or PATH."
      |    Write-Host ""
      |    if ($DOUBLECLICKED) {
      |        Read-Host "Press Enter to exit..."
      |    }
      |    exit 1
      |}
      |
      |$JAVA_OPTS = "$JAVA_OPTS $JAVA_PARAMS"
      |
      |if ($CUSTOM_MAIN_CLASS) {
      |    $MAIN_CLASS = $CUSTOM_MAIN_CLASS
      |} else {
      |    $MAIN_CLASS = $APP_MAIN_CLASS
      |}
      |
      |# Call the application and pass all arguments unchanged.
      |& $JAVACMD -cp \n -jar $APP_MAIN_CLASS
      |exit $LASTEXITCODE""".stripMargin.replaceAll("VERSION_HERE", version)
    IO.write(file, content)
    file
  }

}
