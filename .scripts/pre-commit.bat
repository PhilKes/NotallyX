@echo off
setlocal enabledelayedexpansion

rem Capture the list of initially staged Kotlin files
set "initial_staged_files="
for /f "delims=" %%f in ('git diff --name-only --cached -- "*.kt"') do (
    set "initial_staged_files=!initial_staged_files! %%f,"
)

rem Check if there are any staged Kotlin files
if "%initial_staged_files%"=="" (
    echo No Kotlin files staged for commit.
    exit /b 0
)

rem Remove the trailing comma from the list of formatted files
set "formatted_files=%initial_staged_files:~0,-1%"

echo Formatting Kotlin files: %formatted_files%
call gradlew ktfmtPrecommit --include-only="%formatted_files%"

rem Check if the formatting command was successful
if errorlevel 1 (
    echo Kotlin formatting failed. Please fix the issues.
    exit /b 1
)

rem Re-stage only the initially staged Kotlin files
for %%f in (%initial_staged_files%) do (
    git add "%%f"
)

echo Kotlin files formatted

exit /b 0
