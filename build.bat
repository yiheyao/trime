@echo off
set JAVA_HOME=C:\Users\Administrator\.jdks\jdk-17
set ANDROID_HOME=C:\Users\Administrator\AppData\Local\Android\Sdk
set ANDROID_SDK_ROOT=%ANDROID_HOME%
set ANDROID_NDK_HOME=%ANDROID_HOME%\ndk\28.0.13004108
set GRADLE_USER_HOME=C:\Users\Administrator\.gradle
set PATH=C:\Windows\System32;C:\Windows;C:\Users\Administrator\AppData\Local\Programs\Python\Python311;C:\Users\Administrator\AppData\Local\Programs\Python\Python311\Scripts;C:\Program Files\Git\cmd;C:\Program Files\Git\mingw64\bin;%ANDROID_NDK_HOME%\toolchains\llvm\prebuilt\windows-x86_64\bin;%ANDROID_HOME%\cmake\3.31.6\bin;%PATH%
"C:\Users\Administrator\.gradle\wrapper\dists\gradle-9.5.1-all\3mo7ofu40rhxhyro6vr9xd6jp\gradle-9.5.1\bin\gradle.bat" --no-daemon assembleDebug --info > build.log 2>&1
echo Exit code: %ERRORLEVEL%
