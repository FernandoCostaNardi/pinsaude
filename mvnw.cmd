@echo off
if defined MAVEN_HOME (
  call "%MAVEN_HOME%\bin\mvn.cmd" %*
  goto :end
)
if exist "C:\ProgramData\chocolatey\lib\maven\apache-maven-3.6.3\bin\mvn.cmd" (
  call "C:\ProgramData\chocolatey\lib\maven\apache-maven-3.6.3\bin\mvn.cmd" %*
  goto :end
)
echo ERROR: Maven nao encontrado. Instale via Chocolatey: choco install maven
exit /b 1
:end
