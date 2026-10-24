@echo off
rem Starts the BestPath Data Tool. Double-click this file.
rem If JAVA_HOME points to a folder without Java, ignore it and use Java from PATH.
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" set JAVA_HOME=
cd /d "%~dp0.."
rem Full path: this PC does not run programs from the current folder by name.
call "%~dp0..\gradlew.bat" -p tool run --console=plain
if errorlevel 1 pause
