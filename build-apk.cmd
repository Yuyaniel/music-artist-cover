@echo off
setlocal
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
set "SDK_DIR=C:/Users/Administrator/AppData/Local/Android/Sdk"
set "GRADLE=C:\Users\Administrator\.gradle\wrapper\dists\gradle-9.5.0-bin\bvnork1r7n8i6kp5cnkibsc9q\gradle-9.5.0\bin\gradle.bat"
set "BUILD_TOOLS=C:\Users\Administrator\AppData\Local\Android\Sdk\build-tools\36.0.0"

set "UNSIGNED=app\build\outputs\apk\release\app-release-unsigned.apk"
set "SIGNED=app\build\outputs\apk\release\app-release.apk"

if not exist "local.properties" echo sdk.dir=%SDK_DIR%> local.properties

if not exist "release.keystore" goto makekey
echo [1/4] release.keystore 已存在，跳过生成
goto build

:makekey
echo [1/4] 生成签名证书 release.keystore ...
"%JAVA_HOME%\bin\keytool" -genkeypair -keystore release.keystore -alias arl -keyalg RSA -keysize 2048 -validity 10000 -storepass audioroutelock -keypass audioroutelock -dname "CN=Music Artist Cover"
if errorlevel 1 exit /b 1

:build
echo [2/4] 执行 assembleRelease ...
call "%GRADLE%" assembleRelease --console=plain
if errorlevel 1 exit /b 1

if not exist "%UNSIGNED%" exit /b 1

echo [3/4] 签名 APK ...
"%BUILD_TOOLS%\apksigner.bat" sign --ks release.keystore --ks-pass pass:audioroutelock --ks-key-alias arl --out "%SIGNED%" "%UNSIGNED%"
if errorlevel 1 exit /b 1

echo [4/4] 校验签名 ...
"%BUILD_TOOLS%\apksigner.bat" verify "%SIGNED%"
if errorlevel 1 exit /b 1

echo.
echo 完成：%SIGNED%
endlocal
