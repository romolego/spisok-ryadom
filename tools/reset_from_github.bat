@echo off
cd /d D:\projects\spisok-ryadom

echo ========================================
echo RESTORE LOCAL PROJECT FROM GITHUB
echo ALL LOCAL UNCOMMITTED CHANGES WILL BE LOST
echo ========================================
echo.
echo Press Enter to continue...
pause >nul

git fetch origin
git reset --hard origin/main
git clean -fd

echo.
echo ========================================
echo LOCAL PROJECT RESTORED FROM GITHUB
echo ========================================
git status
pause
