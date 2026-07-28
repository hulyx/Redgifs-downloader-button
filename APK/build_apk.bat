@echo off
echo =============================================
echo  Redgifs Downloader APK Builder
echo =============================================
echo.

set PROJECT_DIR=%~dp0
set OUTPUT_DIR=%PROJECT_DIR%

echo [1/3] Building APK with Gradle...
cd /d "%PROJECT_DIR%"

if not exist "gradlew.bat" (
    echo ERROR: gradlew.bat not found
    echo Make sure Gradle wrapper is properly set up
    pause
    exit /b 1
)

call gradlew.bat assembleDebug

if %ERRORLEVEL% neq 0 (
    echo.
    echo BUILD FAILED
    echo Check the error messages above
    pause
    exit /b 1
)

echo.
echo [2/3] Copying APK to project folder...

set APK_SRC=%PROJECT_DIR%app\build\outputs\apk\debug\app-debug.apk
set APK_DST=%OUTPUT_DIR%RedgifsDownloader-v1.1.apk

if exist "%APK_SRC%" (
    copy "%APK_SRC%" "%APK_DST%" >nul
    echo APK copied to: %APK_DST%
) else (
    echo WARNING: APK not found at expected location
    echo Searching for APK...
    for /r "%PROJECT_DIR%app\build\outputs" %%f in (*.apk) do (
        echo Found: %%f
        copy "%%f" "%APK_DST%" >nul
    )
)

echo.
echo [3/3] Done!
echo.
echo APK location: %APK_DST%
echo.
echo To install on Android device:
echo   1. Enable "Install from unknown sources" in Settings
echo   2. Transfer the APK to your device
echo   3. Open the APK file to install
echo.
pause
