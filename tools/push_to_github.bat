@echo off
cd /d D:\projects\spisok-ryadom

echo ========================================
echo PUSH LOCAL CHANGES TO GITHUB
echo ========================================
echo.
echo Press Enter to continue...
pause >nul

for /f "delims=" %%i in ('powershell -NoProfile -Command "Get-Date -Format ''yyyy-MM-dd HH:mm:ss''"') do set COMMIT_TS=%%i

git add .
git commit -m "Auto sync %COMMIT_TS%"
git push origin main

echo.
echo ========================================
echo DONE
echo ========================================
git status
pause
