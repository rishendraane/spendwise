@echo off
setlocal
set DIRNAME=%~dp0
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%
if not defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
goto init
:findJavaFromJavaHome
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if exist "%JAVA_EXE%" goto init
set JAVA_EXE=java.exe
goto init
:init
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set DEFAULT_JVM_OPTS=-Xmx64m
set JVM_OPTS=%DEFAULT_JVM_OPTS%
for %%I in (%*) do call :findJvmOptions "%%I"
if "%JVM_OPTS%" == "%DEFAULT_JVM_OPTS%" set JVM_OPTS=
"%JAVA_EXE%" %JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
goto :eof
:findJvmOptions
set ARG=%~1
if "%ARG:~0,2%" == "-D" set JVM_OPTS=%JVM_OPTS% %ARG%
goto :eof
