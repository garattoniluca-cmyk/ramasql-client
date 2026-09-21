@echo off
rem Avvia RamaSQL Client in sviluppo: compila e apre la finestra.
rem Cerca da solo il JDK 25 (Temurin), anche se JAVA_HOME non e' ancora aggiornato in questa sessione.
setlocal
cd /d "%~dp0"
for /d %%J in ("%ProgramFiles%\Eclipse Adoptium\jdk-25*") do set "JAVA_HOME=%%~fJ"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo JDK 25 non trovato. Installalo con:  winget install EclipseAdoptium.Temurin.25.JDK
  pause
  exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "%~dp0mvnw.cmd" -q install -DskipTests
if errorlevel 1 (
  echo.
  echo COMPILAZIONE FALLITA
  pause
  exit /b 1
)
call "%~dp0mvnw.cmd" -q -pl app exec:java
