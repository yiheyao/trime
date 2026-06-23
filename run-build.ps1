# Stop any existing Java/Gradle processes
Stop-Process -Name java -Force -ErrorAction SilentlyContinue
Stop-Process -Name javaw -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

# Clear CMake cache
Remove-Item 'd:\project\andriod\trime\app\.cxx' -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item 'd:\project\andriod\trime\app\build' -Recurse -Force -ErrorAction SilentlyContinue

# Build PATH for cmd.exe - C:\Windows must be first so python3.bat is found
$cmdPath = 'C:\Windows\System32;C:\Windows;C:\Users\Administrator\AppData\Local\Programs\Python\Python311;C:\Users\Administrator\AppData\Local\Programs\Python\Python311\Scripts;C:\Program Files\Git\cmd;C:\Program Files\Git\mingw64\bin;C:\Users\Administrator\AppData\Local\Android\Sdk\ndk\28.0.13004108\toolchains\llvm\prebuilt\windows-x86_64\bin;C:\Users\Administrator\AppData\Local\Android\Sdk\cmake\3.31.6\bin'

$gradleBat = 'C:\Users\Administrator\.gradle\wrapper\dists\gradle-9.5.1-all\3mo7ofu40rhxhyro6vr9xd6jp\gradle-9.5.1\bin\gradle.bat'

# Run Gradle via cmd.exe with explicit PATH
$cmd = "set JAVA_HOME=C:\Users\Administrator\.jdks\jdk-17&& set ANDROID_HOME=C:\Users\Administrator\AppData\Local\Android\Sdk&& set ANDROID_SDK_ROOT=C:\Users\Administrator\AppData\Local\Android\Sdk&& set ANDROID_NDK_HOME=C:\Users\Administrator\AppData\Local\Android\Sdk\ndk\28.0.13004108&& set GRADLE_USER_HOME=C:\Users\Administrator\.gradle&& set PATH=$cmdPath&& `"$gradleBat`" --no-daemon assembleDebug"
$process = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $cmd -WorkingDirectory 'd:\project\andriod\trime' -PassThru -NoNewWindow

# Wait for build (max 30 minutes)
$timedOut = $null
$process | Wait-Process -Timeout 1800 -ErrorAction SilentlyContinue -ErrorVariable timedOut

if ($timedOut) {
    Write-Host "TIMEOUT"
    Stop-Process $process.Id -Force -ErrorAction SilentlyContinue
    $exitCode = -1
} else {
    $exitCode = $process.ExitCode
    Write-Host "ExitCode: $exitCode"
}

# Check APK
$apkPath = 'd:\project\andriod\trime\app\build\outputs\apk\debug'
if (Test-Path $apkPath) {
    Get-ChildItem $apkPath | ForEach-Object {
        Write-Host "APK: $($_.Name) - $([math]::Round($_.Length/1MB,2)) MB"
    }
} else {
    Write-Host "No APK found"
}
