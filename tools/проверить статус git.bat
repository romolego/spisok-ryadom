@echo off
cd /d D:\projects\spisok-ryadom

echo ========================================
echo CURRENT GIT STATUS
echo ========================================
echo.

git branch
echo.
git remote -v
echo.
git status
echo.
git log --oneline -n 10

pause
