@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo.
echo =============================================
echo    SISTEMA DE CHAT - GESTOR UNIFICADO
echo =============================================
echo.

:MENU
cls
echo.
echo =============================================
echo    SISTEMA DE CHAT - GESTOR UNIFICADO
echo =============================================
echo.
echo Estado actual de servicios:
echo.

:: Verificar Docker
docker-compose ps >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Docker: Servicios iniciados
) else (
    echo [×] Docker: Servicios detenidos
)

:: Verificar puertos
netstat -ano | findstr ":9090" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Prometheus: http://localhost:9090
) else (
    echo [×] Prometheus: No disponible
)

netstat -ano | findstr ":3000" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Grafana: http://localhost:3000
) else (
    echo [×] Grafana: No disponible
)

netstat -ano | findstr ":3100" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Loki: http://localhost:3100
) else (
    echo [×] Loki: No disponible
)

netstat -ano | findstr ":8089" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] API REST: http://localhost:8089
) else (
    echo [×] API REST: No disponible
)

netstat -ano | findstr ":5000" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Servidor Chat: http://localhost:5000
) else (
    echo [×] Servidor Chat: No disponible
)

echo.
echo ═══════════════════════════════════════════
echo OPCIONES DISPONIBLES:
echo ═══════════════════════════════════════════
echo.
echo 1. Iniciar TODO (Docker + API REST + Interface Vistas)
echo 2. Solo iniciar servicios Docker (Prometheus + Grafana + Loki)
echo 3. Solo iniciar Interface de Vistas
echo 4. Solo iniciar API REST + Interface Vistas
echo 5. Solo iniciar API REST
echo 6. Detener servicios Docker
echo 7. Detener procesos Java
echo 8. Detener TODO (Docker + Java)
echo 9. Verificar estado detallado
echo 0. Salir
echo.
set /p choice="Selecciona una opcion (0-9): "

if "%choice%"=="1" goto START_ALL
if "%choice%"=="2" goto START_DOCKER
if "%choice%"=="3" goto START_VISTAS
if "%choice%"=="4" goto START_API_VISTAS
if "%choice%"=="5" goto START_API
if "%choice%"=="6" goto STOP_DOCKER
if "%choice%"=="7" goto STOP_JAVA
if "%choice%"=="8" goto STOP_ALL
if "%choice%"=="9" goto CHECK_STATUS
if "%choice%"=="0" goto EXIT

echo.
echo [ERROR] Opcion invalida (0-9). Presiona cualquier tecla para continuar...
pause >nul
goto MENU

:START_ALL
echo.
echo [INFO] Iniciando todos los servicios...
echo.

:: Iniciar Docker primero
echo [1/2] Iniciando servicios Docker...
start "Docker Services" cmd /c "echo [INFO] Iniciando servicios Docker (Prometheus + Grafana + Loki)... && docker-compose up -d && if errorlevel 1 (echo [ERROR] No se pudieron iniciar los servicios Docker && pause) else (echo [SUCCESS] Servicios Docker iniciados correctamente && echo URLs disponibles: && echo - Prometheus: http://localhost:9090 && echo - Grafana: http://localhost:3000 && echo - Loki: http://localhost:3100 && echo. && echo Ventana se cerrara en 10 segundos... && timeout /t 10) && exit"

echo [SUCCESS] Comando de Docker ejecutado en ventana separada
echo [INFO] Esperando a que Docker inicie (10 segundos)...
timeout /t 10 >nul
echo.

:: Esperar a que los servicios estén listos
echo [INFO] Esperando a que los servicios Docker esten listos...
timeout /t 5 >nul

:: Verificar que los servicios estén ejecutándose
docker-compose ps | findstr "Up" >nul
if !errorlevel! neq 0 (
    echo [ERROR] Los servicios Docker no se iniciaron correctamente
    pause
    goto MENU
)

:: Iniciar API REST
echo [2/3] Iniciando API REST...
echo.
start "API REST" cmd /c "cd /d "%~dp0" && java -jar "Aplicacion\RestAPI\target\RestAPI-1.0-SNAPSHOT.jar" && echo. && echo API REST se ha cerrado. Presiona cualquier tecla para continuar... && pause >nul"

echo [SUCCESS] API REST iniciada en ventana separada (puerto 8089)
timeout /t 3 >nul

:: Iniciar Interface de Vistas
echo [3/3] Iniciando Interface de Vistas...
echo.
echo [INFO] Se abriran ambas aplicaciones en ventanas separadas
echo [INFO] URLs disponibles:
echo       - Servidor Chat: http://localhost:5000
echo       - API REST: http://localhost:8089
echo       - Prometheus: http://localhost:9090
echo       - Grafana: http://localhost:3000 (admin/admin123)
 echo       - Loki: http://localhost:3100
echo.

cd /d "%~dp0\Presentacion\Vistas"
start "Interface de Vistas" cmd /c "java -cp "target\Vistas-1.0-SNAPSHOT.jar;target\lib\*" com.arquitectura.entidades.vistas.Vistas && echo. && echo La aplicacion se ha cerrado. Presiona cualquier tecla para continuar... && pause >nul"

echo [SUCCESS] Interface de Vistas iniciada en ventana separada
echo.
echo [INFO] Sistema completamente iniciado. Presiona cualquier tecla para volver al menu...
pause >nul
cd /d "%~dp0"
goto MENU

:START_DOCKER
echo.
echo [INFO] Iniciando servicios Docker (Prometheus + Grafana + Loki)...
echo.

start "Docker Services" cmd /c "echo ========================================== && echo   INICIANDO SERVICIOS DOCKER && echo ========================================== && echo. && echo [INFO] Iniciando Prometheus, Grafana y Loki... && echo. && docker-compose up -d && if errorlevel 1 (echo. && echo [ERROR] No se pudieron iniciar los servicios Docker && echo [INFO] Verifica que Docker Desktop este ejecutandose && echo. && pause) else (echo. && echo [SUCCESS] Servicios Docker iniciados correctamente && echo. && echo URLs disponibles: && echo - Prometheus: http://localhost:9090 && echo - Grafana: http://localhost:3000 (usuario: admin, contraseña: admin123) && echo - Loki: http://localhost:3100 && echo. && echo Presiona cualquier tecla para cerrar esta ventana... && pause >nul)"

echo [SUCCESS] Comando de Docker ejecutado en ventana separada
echo [INFO] Los servicios se estan iniciando en segundo plano
echo.
pause
goto MENU

:START_VISTAS
echo.
echo [INFO] Iniciando Interface de Vistas...
echo.

:: Verificar que el JAR existe
if not exist "Presentacion\Vistas\target\Vistas-1.0-SNAPSHOT.jar" (
    echo [ERROR] JAR de Vistas no encontrado
    echo [INFO] Ejecuta primero: mvn clean package -DskipTests
    pause
    goto MENU
)

echo [INFO] Ejecutando Interface de Usuario en ventana separada...
start "Servidor Chat - Interface de Vistas" cmd /c "cd /d "%~dp0\Presentacion\Vistas" && echo ========================================== && echo     SERVIDOR CHAT - INTERFACE DE VISTAS && echo ========================================== && echo. && echo [INFO] Iniciando servidor de chat en puerto 5000... && echo [INFO] Presiona Ctrl+C para detener el servidor && echo. && java -cp "target\Vistas-1.0-SNAPSHOT.jar;target\lib\*" com.arquitectura.entidades.vistas.Vistas && echo. && echo [INFO] La aplicacion se ha cerrado. Presiona cualquier tecla para continuar... && pause >nul"

echo [SUCCESS] Interface de Vistas iniciada en ventana separada
echo [INFO] URLs disponibles:
echo       - Servidor Chat: http://localhost:5000
echo       - Prometheus Metrics: http://localhost:5100/metrics
echo.
pause
goto MENU

:START_API_VISTAS
echo.
echo [INFO] Iniciando API REST + Interface de Vistas...
echo.

:: Verificar que los JARs existen
if not exist "Aplicacion\RestAPI\target\RestAPI-1.0-SNAPSHOT.jar" (
    echo [ERROR] JAR de API REST no encontrado
    echo [INFO] Ejecuta primero: mvn clean package -DskipTests
    pause
    goto MENU
)

if not exist "Presentacion\Vistas\target\Vistas-1.0-SNAPSHOT.jar" (
    echo [ERROR] JAR de Vistas no encontrado
    echo [INFO] Ejecuta primero: mvn clean package -DskipTests
    pause
    goto MENU
)

:: Iniciar API REST
echo [1/2] Iniciando API REST...
start "API REST" cmd /c "cd /d "%~dp0" && java -jar "Aplicacion\RestAPI\target\RestAPI-1.0-SNAPSHOT.jar" && echo. && echo API REST se ha cerrado. Presiona cualquier tecla para continuar... && pause >nul"

echo [SUCCESS] API REST iniciada en ventana separada (puerto 8089)
timeout /t 3 >nul

:: Iniciar Interface de Vistas
echo [2/2] Iniciando Interface de Vistas...
cd /d "%~dp0\Presentacion\Vistas"
start "Interface de Vistas" cmd /c "java -cp "target\Vistas-1.0-SNAPSHOT.jar;target\lib\*" com.arquitectura.entidades.vistas.Vistas && echo. && echo La aplicacion se ha cerrado. Presiona cualquier tecla para continuar... && pause >nul"

echo [SUCCESS] Ambos servicios iniciados en ventanas separadas
echo [INFO] URLs disponibles:
echo       - Servidor Chat: http://localhost:5000
echo       - API REST: http://localhost:8089
echo.
pause
cd /d "%~dp0"
goto MENU

:START_API
echo.
echo [INFO] Iniciando solo API REST...
echo.

:: Verificar que el JAR existe
if not exist "Aplicacion\RestAPI\target\RestAPI-1.0-SNAPSHOT.jar" (
    echo [ERROR] JAR de API REST no encontrado
    echo [INFO] Ejecuta primero: mvn clean package -DskipTests
    pause
    goto MENU
)

echo [INFO] Ejecutando API REST en ventana separada...
start "API REST" cmd /c "cd /d "%~dp0" && echo ========================================== && echo           API REST - SERVIDOR && echo ========================================== && echo. && echo [INFO] Iniciando API REST en puerto 8089... && echo [INFO] Presiona Ctrl+C para detener el servidor && echo. && java -jar "Aplicacion\RestAPI\target\RestAPI-1.0-SNAPSHOT.jar" && echo. && echo [INFO] API REST se ha cerrado. Presiona cualquier tecla para continuar... && pause >nul"

echo [SUCCESS] API REST iniciada en ventana separada (puerto 8089)
echo [INFO] URLs disponibles:
echo       - API REST: http://localhost:8089
echo       - WebSocket Metrics: ws://localhost:8089/ws/metrics
echo       - WebSocket Logs: ws://localhost:8089/ws/logs
echo.
pause
goto MENU

:STOP_DOCKER
echo.
echo [INFO] Deteniendo servicios Docker...
echo.

start "Stop Docker Services" cmd /c "echo ========================================== && echo   DETENIENDO SERVICIOS DOCKER && echo ========================================== && echo. && echo [INFO] Deteniendo Prometheus, Grafana y Loki... && echo. && docker-compose down && if errorlevel 0 (echo. && echo [SUCCESS] Servicios Docker detenidos correctamente) else (echo. && echo [ERROR] Error al detener los servicios Docker) && echo. && echo Presiona cualquier tecla para cerrar esta ventana... && pause >nul"

echo [SUCCESS] Comando de parada de Docker ejecutado en ventana separada
echo [INFO] Los servicios se estan deteniendo en segundo plano
echo.
pause
goto MENU

:STOP_JAVA
echo.
echo [INFO] Deteniendo procesos Java del sistema de chat...
echo.

start "Stop Java Processes" cmd /c "echo ========================================== && echo   DETENIENDO PROCESOS JAVA && echo ========================================== && echo. && echo [INFO] Buscando procesos Java del sistema de chat... && echo. && tasklist /fi \"imagename eq java.exe\" /v 2>nul | findstr /i \"RestAPI\\|Vistas\\|Bootstrap\" >nul && if errorlevel 0 (echo [INFO] Encontrados procesos Java del proyecto && echo. && echo [INFO] Procesos Java activos relacionados con el proyecto: && for /f \"tokens=2\" %%a in ('tasklist /fi \"imagename eq java.exe\" /fo csv ^| findstr /i \"RestAPI\\|Vistas\\|Bootstrap\"') do (echo - PID: %%a) && echo. && echo [WARNING] ¿Estas seguro de que deseas cerrar estos procesos? (s/n) && set /p confirm=\"\" && if /i \"!confirm!\"==\"s\" (echo [INFO] Cerrando procesos Java... && for /f \"tokens=2 delims=,\" %%a in ('tasklist /fi \"imagename eq java.exe\" /fo csv ^| findstr /i \"RestAPI\\|Vistas\\|Bootstrap\"') do (set pid=%%a && set pid=!pid:\"=! && echo [INFO] Cerrando proceso PID: !pid! && taskkill /pid !pid! /t >nul 2>&1) && timeout /t 2 >nul && tasklist /fi \"imagename eq java.exe\" /v 2>nul | findstr /i \"RestAPI\\|Vistas\\|Bootstrap\" >nul && if errorlevel 0 (echo [WARNING] Algunos procesos no se cerraron correctamente, forzando cierre... && for /f \"tokens=2 delims=,\" %%a in ('tasklist /fi \"imagename eq java.exe\" /fo csv ^| findstr /i \"RestAPI\\|Vistas\\|Bootstrap\"') do (set pid=%%a && set pid=!pid:\"=! && taskkill /pid !pid! /f >nul 2>&1)) && echo [SUCCESS] Procesos Java detenidos) else (echo [INFO] Operacion cancelada)) else (echo [INFO] No se encontraron procesos Java del proyecto ejecutandose) && echo. && echo Presiona cualquier tecla para cerrar esta ventana... && pause >nul"

echo [SUCCESS] Comando de parada de Java ejecutado en ventana separada
echo [INFO] Los procesos se estan gestionando en segundo plano
echo.
pause
goto MENU

:STOP_ALL
echo.
echo [INFO] Deteniendo TODOS los servicios (Docker + Java)...
echo.

:: Detener procesos Java primero
echo [1/2] Deteniendo procesos Java...

start "Stop All Java" cmd /c "echo [INFO] Cerrando procesos Java del proyecto... && tasklist /fi \"imagename eq java.exe\" /v 2>nul | findstr /i \"RestAPI\\|Vistas\\|Bootstrap\" >nul && if errorlevel 0 (for /f \"tokens=2 delims=,\" %%a in ('tasklist /fi \"imagename eq java.exe\" /fo csv ^| findstr /i \"RestAPI\\|Vistas\\|Bootstrap\"') do (set pid=%%a && set pid=!pid:\"=! && taskkill /pid !pid! /f >nul 2>&1) && echo [SUCCESS] Procesos Java detenidos) else (echo [INFO] No habia procesos Java ejecutandose) && timeout /t 3"

:: Detener servicios Docker
echo [2/2] Deteniendo servicios Docker...
start "Stop All Docker" cmd /c "echo [INFO] Deteniendo servicios Docker... && docker-compose down && if errorlevel 0 (echo [SUCCESS] Servicios Docker detenidos correctamente) else (echo [ERROR] Error al detener los servicios Docker) && timeout /t 3"

echo.
echo [SUCCESS] Todos los servicios han sido detenidos
pause
goto MENU

:CHECK_STATUS
cls
echo.
echo =============================================
echo       ESTADO DETALLADO DEL SISTEMA
echo =============================================
echo.

echo [INFO] Servicios Docker:
echo ─────────────────────────────────────────────
docker-compose ps 2>nul
if !errorlevel! neq 0 (
    echo [ERROR] Docker Compose no disponible o servicios no iniciados
) else (
    echo [INFO] Estado de contenedores mostrado arriba
)

echo.
echo [INFO] Puertos en uso:
echo ─────────────────────────────────────────────

netstat -ano | findstr ":9090" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Puerto 9090 - Prometheus ACTIVO
) else (
    echo [×] Puerto 9090 - Prometheus NO ACTIVO
)

netstat -ano | findstr ":3000" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Puerto 3000 - Grafana ACTIVO
) else (
    echo [×] Puerto 3000 - Grafana NO ACTIVO
)

netstat -ano | findstr ":3100" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Puerto 3100 - Loki ACTIVO
) else (
    echo [×] Puerto 3100 - Loki NO ACTIVO
)

netstat -ano | findstr ":8089" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Puerto 8089 - API REST ACTIVO
) else (
    echo [×] Puerto 8089 - API REST NO ACTIVO
)

netstat -ano | findstr ":5000" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Puerto 5000 - Servidor Chat ACTIVO
) else (
    echo [×] Puerto 5000 - Servidor Chat NO ACTIVO
)

echo.
echo [INFO] Procesos Java del proyecto:
echo ─────────────────────────────────────────────

tasklist /fi "imagename eq java.exe" /v 2>nul | findstr /i "RestAPI\|Vistas\|Bootstrap" >nul
if !errorlevel! equ 0 (
    echo [✓] Procesos Java ACTIVOS:
    for /f "tokens=1,2,9" %%a in ('tasklist /fi "imagename eq java.exe" /fo table ^| findstr /i "RestAPI\|Vistas\|Bootstrap"') do (
        echo     - %%a (PID: %%b^) - %%c
    )
) else (
    echo [×] No hay procesos Java del proyecto ejecutándose
)

echo.
echo [INFO] Archivos necesarios:
echo ─────────────────────────────────────────────

if exist "Presentacion\Vistas\target\Vistas-1.0-SNAPSHOT.jar" (
    echo [✓] JAR de Vistas: Disponible
) else (
    echo [×] JAR de Vistas: NO DISPONIBLE - Ejecuta 'mvn clean package -DskipTests'
)

if exist "docker-compose.yml" (
    echo [✓] Docker Compose: Configurado
) else (
    echo [×] Docker Compose: Archivo no encontrado
)

echo.
echo ═══════════════════════════════════════════
echo URLs DE ACCESO:
echo ═══════════════════════════════════════════
echo - Prometheus: http://localhost:9090
echo - Grafana: http://localhost:3000
echo   Usuario: admin, Contraseña: admin123
echo - Loki: http://localhost:3100
echo.
echo Presiona cualquier tecla para volver al menu...
pause >nul
goto MENU

:EXIT
echo.
echo [INFO] ¿Qué servicios deseas detener antes de salir?
echo.
echo 1. No detener nada
echo 2. Solo Docker
echo 3. Solo Java
echo 4. Todo (Docker + Java)
echo.
set /p exit_choice="Selecciona opcion (1-4): "

if "%exit_choice%"=="1" (
    echo [INFO] No se detendrán servicios
) else if "%exit_choice%"=="2" (
    echo [INFO] Deteniendo servicios Docker en ventana separada...
    start "Exit - Stop Docker" cmd /c "echo [INFO] Deteniendo servicios Docker... && docker-compose down && echo [SUCCESS] Servicios Docker detenidos && timeout /t 3"
    echo [SUCCESS] Comando enviado a ventana separada
) else if "%exit_choice%"=="3" (
    echo [INFO] Deteniendo procesos Java en ventana separada...
    start "Exit - Stop Java" cmd /c "echo [INFO] Deteniendo procesos Java... && for /f \"tokens=2 delims=,\" %%a in ('tasklist /fi \"imagename eq java.exe\" /fo csv ^| findstr /i \"RestAPI\\|Vistas\\|Bootstrap\" 2^>nul') do (set pid=%%a && set pid=!pid:\"=! && taskkill /pid !pid! /f >nul 2>&1) && echo [SUCCESS] Procesos Java detenidos && timeout /t 3"
    echo [SUCCESS] Comando enviado a ventana separada
) else if "%exit_choice%"=="4" (
    echo [INFO] Deteniendo todos los servicios en ventanas separadas...
    start "Exit - Stop Java" cmd /c "echo [INFO] Deteniendo procesos Java... && for /f \"tokens=2 delims=,\" %%a in ('tasklist /fi \"imagename eq java.exe\" /fo csv ^| findstr /i \"RestAPI\\|Vistas\\|Bootstrap\" 2^>nul') do (set pid=%%a && set pid=!pid:\"=! && taskkill /pid !pid! /f >nul 2>&1) && echo [SUCCESS] Procesos Java detenidos && timeout /t 3"
    start "Exit - Stop Docker" cmd /c "echo [INFO] Deteniendo servicios Docker... && docker-compose down && echo [SUCCESS] Servicios Docker detenidos && timeout /t 3"
    echo [SUCCESS] Comandos enviados a ventanas separadas
) else (
    echo [INFO] Opción no válida, no se detendrán servicios
)

echo.
echo [INFO] Saliendo del sistema...
echo ¡Hasta luego!
timeout /t 2 >nul
exit /b 0