@rem ----------------------------------------------------------------------------
@rem Gradle start-up script for Windows (compact wrapper).
@rem See the POSIX script (gradlew) for a note about the wrapper jar.
@rem ----------------------------------------------------------------------------
@if "%DEBUG%" == "" @echo off
setlocal

set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if defined JAVA_HOME (
    set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
    set JAVACMD=java.exe
)

if not exist "%CLASSPATH%" (
    echo Gradle wrapper jar not found: %CLASSPATH%
    echo Run 'gradle wrapper --gradle-version 8.7' once, or open the project in Android Studio.
    exit /b 1
)

"%JAVACMD%" -Xmx2048m -Xms256m -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
