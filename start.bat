@echo off
rem KijelzoPC start script for Windows
rem Installs a newer signed release first. The updater runs from a copy of the JAR so it can
rem replace it; if the new version ends right after starting, the previous one is restored.
cd /d "%~dp0"
copy /y out\KijelzoPC.jar "%TEMP%\kijelzopc-updater.jar" >nul
java -cp "%TEMP%\kijelzopc-updater.jar" kijelzopc.Updater update

java -Dfile.encoding=utf-8 -jar out\KijelzoPC.jar

java -cp "%TEMP%\kijelzopc-updater.jar" kijelzopc.Updater rollback && java -Dfile.encoding=utf-8 -jar out\KijelzoPC.jar
