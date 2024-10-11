@echo off

rem Count the number of staged Kotlin files
for /f %%i in ('git diff --name-only --cached --numstat -- "*.kt" ^| find /c /v ""') do set staged_files_count=%%i

rem Format only if there are Kotlin files in git's index
if %staged_files_count% gtr 0 (
    rem Format the staged Kotlin files and remove the "app/" prefix
    for /f "delims=" %%f in ('git diff --name-only --cached -- "*.kt" ^| sed "s|^app/||"') do (
        set formatted_files=%%f
        set formatted_files=!formatted_files!, %%f
    )
    rem Remove the trailing comma if necessary
    set formatted_files=%formatted_files:~, -1%

    call gradlew ktfmtPrecommit --include-only="%formatted_files%"

    rem Check if the formatting command was successful
    if errorlevel 1 (
        echo Kotlin formatting failed. Please fix the issues.
        exit /b 1
    )

    rem Add the formatted Kotlin files to the staging area
    git add -A git diff --name-only -- "*.kt"
    echo Kotlin files formatted and changes staged.
)

exit /b 0