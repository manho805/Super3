@echo off
setlocal

pushd "%~dp0android" || exit /b 1

rem Build all APKs (per ABI) using the Gradle wrapper in /android
call gradlew.bat assembleDebug assembleRelease

echo.
echo Build complete.
echo Debug APK:
echo   %~dp0android\app\build\outputs\apk\debug\app-debug.apk
echo Release APK:
echo   %~dp0android\app\build\outputs\apk\release\app-release.apk
echo.

popd

pause
