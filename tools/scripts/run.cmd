@echo off
:: Runner universal para scripts Node.js no contexto Nx (Windows)
:: Usa o caminho completo do node para contornar o PATH truncado do cmd.exe
if exist "C:\Program Files\nodejs\node.exe" (
  "C:\Program Files\nodejs\node.exe" %*
  exit /b %ERRORLEVEL%
)
if exist "C:\Program Files (x86)\nodejs\node.exe" (
  "C:\Program Files (x86)\nodejs\node.exe" %*
  exit /b %ERRORLEVEL%
)
echo ERROR: Node.js nao encontrado em caminhos conhecidos
exit /b 1
