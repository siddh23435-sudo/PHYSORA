@echo off
setlocal
for %%i in ("%~dp0.") do set "MAVEN_PROJECTBASEDIR=%%~fi"
set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
if not exist "%WRAPPER_JAR%" (
  echo Maven Wrapper bootstrap jar is missing: %WRAPPER_JAR% 1>&2
  exit /b 1
)
java "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %ERRORLEVEL%




