@echo off
setlocal
cd /d "%~dp0"

set "REMOTE=https://github.com/Yuyaniel/music-artist-cover.git"

if not exist ".git" (
  echo [1/4] 初始化本地仓库 ...
  git init -b main
  if errorlevel 1 exit /b 1
) else (
  echo [1/4] 已存在本地仓库，跳过初始化
)

echo [2/4] 配置远程地址 %REMOTE%
git remote remove origin >nul 2>&1
git remote add origin %REMOTE%
if errorlevel 1 exit /b 1

echo [3/4] 暂存文件 ...
git add -A

echo [4/4] 提交 ...
git commit -m "feat: 歌手图片匹配 v1.0 —— 本地歌曲扫描与缓存、多歌手拆分、按歌手批量匹配、平台候选挑选与本地覆盖"
if errorlevel 1 (
  echo [提示] 没有新的改动需要提交
)

echo.
echo 本地仓库已就绪。推送命令：
echo   git push origin main
endlocal
