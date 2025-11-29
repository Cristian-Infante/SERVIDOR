@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo.
echo ===============================================
echo         TCP SERVERS API GATEWAY
echo ===============================================

:MENU
cls
echo.
echo ===============================================
echo         TCP SERVERS API GATEWAY
echo ===============================================
echo.
echo Estado actual:

netstat -ano | findstr ":8091" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] API Gateway: ACTIVO - http://localhost:8091/gateway/
) else (
    echo [×] API Gateway: INACTIVO
)

if exist "ApiGateway\target\ApiGateway-1.0-SNAPSHOT.jar" (
    echo [✓] JAR: Compilado y listo
) else (
    echo [×] JAR: No compilado - Ejecuta opción 1
)

echo.
echo ═══════════════════════════════════════════
echo OPCIONES DISPONIBLES:
echo ═══════════════════════════════════════════
echo.
echo 1. Compilar API Gateway
echo 2. Iniciar API Gateway
echo 0. Salir
echo.
set /p choice="Selecciona una opcion (0-2): "

if "%choice%"=="1" goto COMPILE_GATEWAY
if "%choice%"=="2" goto START_GATEWAY
if "%choice%"=="0" goto EXIT

echo.
echo [ERROR] Opcion invalida. Presiona cualquier tecla para continuar...
pause >nul
goto MENU

:COMPILE_GATEWAY
echo.
echo [INFO] Compilando API Gateway...
cd ApiGateway
call mvn clean package -DskipTests
cd ..
echo [SUCCESS] Compilacion completada
pause
goto MENU

:START_GATEWAY
echo.
echo [INFO] Iniciando API Gateway...

if not exist "ApiGateway\target\ApiGateway-1.0-SNAPSHOT.jar" (
    echo [ERROR] JAR no encontrado - Ejecuta opcion 1 primero
    pause
    goto MENU
)

echo [INFO] Ejecutando API Gateway en ventana separada...
start "TCP Servers API Gateway" cmd /k "cd /d "%~dp0\ApiGateway" && echo ========================================== && echo    TCP SERVERS API GATEWAY && echo ========================================== && echo. && echo [INFO] Verificando directorio y dependencias... && echo [INFO] Directorio actual: && cd && echo. && echo [INFO] Verificando Java... && java -version && echo. && echo [INFO] Verificando Maven... && mvn -version && echo. && echo [INFO] Iniciando API Gateway en puerto 8091... && echo [INFO] Presiona Ctrl+C para detener el servidor && echo. && echo URLs disponibles: && echo - Swagger UI: http://localhost:8091/gateway/swagger-ui.html && echo - Health Check: http://localhost:8091/gateway/actuator/health && echo - API Docs: http://localhost:8091/gateway/api-docs && echo - Demo Frontend: ..\frontend-demo.html && echo. && mvn spring-boot:run || (echo. && echo [ERROR] Fallo al iniciar el API Gateway && echo [ERROR] Revisa los mensajes de error anteriores && echo. && pause) && echo. && echo [INFO] API Gateway se ha cerrado. Presiona cualquier tecla para continuar... && pause"

echo [SUCCESS] API Gateway iniciado en ventana separada
echo [INFO] URL: http://localhost:8091/gateway/swagger-ui.html
pause
goto MENU

:EXIT
echo [INFO] Saliendo...
exit /b 0