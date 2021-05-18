echo off
setlocal

set M2=%USERPROFILE%\.m2\repository
set CORE_VERSION=8.0.1
set CORE=%M2%\org\javacc\core\%CORE_VERSION%\core-%CORE_VERSION%.jar

set VERSION=-SNAPSHOT
set JAVA_NUMBER=8.0.2
set JAVA=java-%JAVA_NUMBER%%VERSION%

set TARGET=target\%JAVA%.jar
set CP=%CORE%;%TARGET%

java -cp %CP% jjdoc %*
endlocal
