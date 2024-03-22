@echo off
if "%~1"=="" (
    echo "Syntax: gradlewu <NEW_VERSION>"
) else (
    call gradlew wrapper --gradle-version "%~1" --distribution-type bin
)
