# Stop any existing Java/Gradle processes
Stop-Process -Name java -Force -ErrorAction SilentlyContinue
Stop-Process -Name javaw -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

# Set environment
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\jdk-17'
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_NDK_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk\ndk\28.0.13004108'
$env:GRADLE_USER_HOME = 'C:\Users\Administrator\.gradle'

# Add to PATH: C:\Windows (for python3.bat), Python, Git, NDK, CMake
$env:PATH = 'C:\Windows\System32;C:\Windows;' +
    'C:\Users\Administrator\AppData\Local\Programs\Python\Python311;' +
    'C:\Users\Administrator\AppData\Local\Programs\Python\Python311\Scripts;' +
    'C:\Program Files\Git\cmd;' +
    'C:\Program Files\Git\mingw64\bin;' +
    'C:\Users\Administrator\AppData\Local\Android\Sdk\ndk\28.0.13004108\toolchains\llvm\prebuilt\windows-x86_64\bin;' +
    'C:\Users\Administrator\AppData\Local\Android\Sdk\cmake\3.31.6\bin;' +
    $env:PATH

# Run gradlew.bat assembleDebug (as recommended in README)
Set-Location 'd:\project\andriod\trime'
& '.\gradlew.bat' --no-daemon assembleDebug
$exitCode = $LASTEXITCODE

Write-Host "ExitCode: $exitCode"

# Check APK
$apkPath = 'd:\project\andriod\trime\app\build\outputs\apk\debug'
if (Test-Path $apkPath) {
    Get-ChildItem $apkPath | ForEach-Object {
        Write-Host "APK: $($_.Name) - $([math]::Round($_.Length/1MB,2)) MB"
    }
} else {
    Write-Host "No APK found"
}
