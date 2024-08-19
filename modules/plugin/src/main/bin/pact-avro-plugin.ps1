# pact-avro-plugin launcher script
# Environment:
# JAVA_HOME - location of a JDK home dir (optional if java on path)
# CFG_OPTS  - JVM options (optional)
# Configuration:
# PACT_AVRO_PLUGIN_config.txt found in the PACT_AVRO_PLUGIN_HOME.

$PACT_AVRO_PLUGIN_HOME = $null
$PACT_PLUGIN_VERSION = "0.0.6"
$APP_HOME = $null
$APP_LIB_DIR = $null
$CFG_FILE = $null
$CFG_OPTS = $null
$JAVA_OPTS = $null
$JAVAOK = $null
$JAVAINSTALLED = $null
$BUNDLED_JVM = $null
$JAVACMD = $null
$SCRIPT_CONF_FILE = $null
$SCRIPT_CONF_ARGS = $null
$CUSTOM_MAIN_CLASS = $null
$MAIN_CLASS = $null

$PACT_AVRO_PLUGIN_HOME = $env:PACT_AVRO_PLUGIN_HOME
if ([string]::IsNullOrEmpty($PACT_AVRO_PLUGIN_HOME)) {
    $APP_HOME = Resolve-Path -Path (Join-Path -Path (Split-Path -Parent $MyInvocation.MyCommand.Path) -ChildPath "..")

    # Also set the old env name for backwards compatibility
    $PACT_AVRO_PLUGIN_HOME = Resolve-Path -Path (Join-Path -Path (Split-Path -Parent $MyInvocation.MyCommand.Path) -ChildPath "..")
} else {
    $APP_HOME = $PACT_AVRO_PLUGIN_HOME
}

$APP_LIB_DIR = Join-Path $APP_HOME "lib"

# Detect if we were double clicked, although theoretically A user could manually run cmd /c
$DOUBLECLICKED = $false
if ($MyInvocation.InvocationName -eq "cmd.exe" -and $MyInvocation.BoundParameters.ContainsKey("c")) {
    $DOUBLECLICKED = $true
}

# Load the config file of extra options.
$CFG_FILE = Join-Path $APP_HOME "PACT_AVRO_PLUGIN_config.txt"
$CFG_OPTS = Get-Content -Path $CFG_FILE -Raw -ErrorAction SilentlyContinue

# Use the value of the JAVA_OPTS environment variable if defined, rather than the config.
$JAVA_OPTS = $env:JAVA_OPTS
if ([string]::IsNullOrEmpty($JAVA_OPTS)) {
    $JAVA_OPTS = $CFG_OPTS
}

# We keep in $JAVA_PARAMS all -J-prefixed and -D-prefixed arguments
# "-J" is stripped, "-D" is left as is, and everything is appended to JAVA_OPTS
$JAVA_PARAMS = @()
$APP_ARGS = @()

$APP_CLASSPATH = $null
$APP_MAIN_CLASS = "$($APP_LIB_DIR)\io.pact.plugin-$PACT_PLUGIN_VERSION-launcher.jar"
$SCRIPT_CONF_FILE = Join-Path $APP_HOME "conf\application.ini"

# Bundled JRE has priority over standard environment variables
if ($env:BUNDLED_JVM) {
    $JAVACMD = Join-Path $env:BUNDLED_JVM "bin\java.exe"
} else {
    if ($env:JAVACMD) {
        $JAVACMD = $env:JAVACMD
    } else {
        if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
            $JAVACMD = Join-Path $env:JAVA_HOME "bin\java.exe"
        }
    }
}

if ([string]::IsNullOrEmpty($JAVACMD)) {
    $JAVACMD = "java"
}

# Detect if this java is ok to use.
$JAVAINSTALLED = $false
$JAVACMDOutput = & $JAVACMD -version 2>&1
if ($JAVACMDOutput -match "java|openjdk") {
    $JAVAINSTALLED = $true
}

$JAVAOK = $true
if (-not $JAVAINSTALLED) {
    Write-Host ""
    Write-Host "A Java JDK is not installed or can't be found."
    if ($env:JAVA_HOME) {
        Write-Host "JAVA_HOME = $env:JAVA_HOME"
    }
    Write-Host ""
    Write-Host "Please go to"
    Write-Host "  http://www.oracle.com/technetwork/java/javase/downloads/index.html"
    Write-Host "and download a valid Java JDK and install before running pact-avro-plugin."
    Write-Host ""
    Write-Host "If you think this message is in error, please check"
    Write-Host "your environment variables to see if ""java.exe"" and ""javac.exe"" are"
    Write-Host "available via JAVA_HOME or PATH."
    Write-Host ""
    if ($DOUBLECLICKED) {
        Read-Host "Press Enter to exit..."
    }
    exit 1
}

$JAVA_OPTS = "$JAVA_OPTS $JAVA_PARAMS"

if ($CUSTOM_MAIN_CLASS) {
    $MAIN_CLASS = $CUSTOM_MAIN_CLASS
} else {
    $MAIN_CLASS = $APP_MAIN_CLASS
}

# Call the application and pass all arguments unchanged.
& $JAVACMD -cp \n -jar $APP_MAIN_CLASS
exit $LASTEXITCODE