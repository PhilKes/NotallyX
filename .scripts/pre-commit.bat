@echo off

CALL ./gradlew.bat ktfmtPrecommit

IF ERRORLEVEL 1 (
    echo Kotlin formatting failed. Please fix the issues.
    exit /B 1
) ELSE (
    git add .
)

echo Kotlin files formatted and changes staged.
exit /B 0