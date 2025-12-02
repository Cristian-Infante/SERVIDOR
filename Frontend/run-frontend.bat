@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo.
echo =============================================
echo    FRONTEND DASHBOARD - GESTOR UNIFICADO
echo =============================================
echo.

:MENU
cls
echo.
echo =============================================
echo    FRONTEND DASHBOARD - GESTOR UNIFICADO
echo =============================================
echo.
echo Estado actual de servicios:
echo.

:: Verificar Docker
docker compose ps >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Docker: Servicios iniciados
) else (
    echo [×] Docker: Servicios detenidos
)

:: Verificar puertos del frontend
netstat -ano | findstr ":8083" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Frontend Dashboard: http://localhost:8083
) else (
    echo [×] Frontend Dashboard: No disponible
)

netstat -ano | findstr ":3002" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Grafana: http://localhost:3002
) else (
    echo [×] Grafana: No disponible
)

netstat -ano | findstr ":9092" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Prometheus: http://localhost:9092
) else (
    echo [×] Prometheus: No disponible
)

netstat -ano | findstr ":3102" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] Loki: http://localhost:3102
) else (
    echo [×] Loki: No disponible
)

:: Verificar API Gateway (externo)
netstat -ano | findstr ":8091" >nul 2>&1
if !errorlevel! equ 0 (
    echo [✓] API Gateway: http://localhost:8091 (externo)
) else (
    echo [!] API Gateway: No detectado - Necesario para métricas
)

echo.
echo ═══════════════════════════════════════════
echo OPCIONES DISPONIBLES:
echo ═══════════════════════════════════════════
echo.
echo 1. Iniciar TODO (Docker Frontend: Dashboard + Grafana + Prometheus + Loki)
echo 2. Solo iniciar servicios Docker (Grafana + Prometheus + Loki)
echo 3. Solo abrir Frontend Dashboard
echo 4. Solo abrir Grafana
echo 5. Solo abrir Prometheus
echo 6. Detener servicios Docker Frontend
echo 7. Verificar estado detallado
echo 8. Limpiar datos y reiniciar
echo 0. Salir
echo.
set /p choice="Selecciona una opcion (0-8): "

if "%choice%"=="1" goto START_ALL
if "%choice%"=="2" goto START_DOCKER
if "%choice%"=="3" goto OPEN_FRONTEND
if "%choice%"=="4" goto OPEN_GRAFANA
if "%choice%"=="5" goto OPEN_PROMETHEUS
if "%choice%"=="6" goto STOP_DOCKER
if "%choice%"=="7" goto CHECK_STATUS
if "%choice%"=="8" goto CLEAN_RESTART
if "%choice%"=="0" goto EXIT

echo.
echo [ERROR] Opcion invalida (0-8). Presiona cualquier tecla para continuar...
pause >nul
goto MENU

:START_ALL
echo.
echo [INFO] Iniciando todos los servicios del Frontend Dashboard...
echo.

:: Iniciar Docker
echo [1/1] Iniciando servicios Docker Frontend...
start "Docker Frontend Services" cmd /c "echo [INFO] Iniciando servicios Docker Frontend (Dashboard + Grafana + Prometheus + Loki)... && docker compose up -d && if errorlevel 1 (echo [ERROR] No se pudieron iniciar los servicios Docker Frontend && pause) else (echo [SUCCESS] Servicios Docker Frontend iniciados correctamente && echo URLs disponibles: && echo - Frontend Dashboard: http://localhost:8083 && echo - Grafana: http://localhost:3002 (admin/admin123) && echo - Prometheus: http://localhost:9092 && echo - Loki: http://localhost:3102 && echo. && echo [INFO] Para metricas completas, asegurate de que el API Gateway este corriendo en puerto 8091 && echo. && echo Ventana se cerrara en 15 segundos... && timeout /t 15) && exit"

echo [SUCCESS] Comando de Docker Frontend ejecutado en ventana separada
echo [INFO] Esperando a que Docker inicie (10 segundos)...
timeout /t 10 >nul
echo.

:: Esperar a que los servicios estén listos
echo [INFO] Esperando a que los servicios Docker esten listos...
timeout /t 5 >nul

:: Verificar que los servicios estén ejecutándose
docker compose ps | findstr "Up" >nul
if !errorlevel! neq 0 (
    echo [ERROR] Los servicios Docker Frontend no se iniciaron correctamente
    pause
    goto MENU
)

echo [SUCCESS] Frontend Dashboard iniciado completamente
echo.
echo [INFO] URLs disponibles:
echo       - Frontend Dashboard: http://localhost:8083
echo       - Grafana Dashboard: http://localhost:3002 (admin/admin123)
echo       - Prometheus: http://localhost:9092
echo       - Loki: http://localhost:3102
echo.
echo [INFO] IMPORTANTE: Para métricas completas, asegúrate de que el API Gateway esté corriendo en puerto 8091
echo.
echo [INFO] Sistema Frontend completamente iniciado. Presiona cualquier tecla para volver al menu...
pause >nul
goto MENU

:START_DOCKER
echo.
echo [INFO] Iniciando servicios Docker Frontend (Grafana + Prometheus + Loki)...
echo.

start "Docker Frontend Services" cmd /c "echo ========================================== && echo   INICIANDO SERVICIOS DOCKER FRONTEND && echo ========================================== && echo. && echo [INFO] Iniciando Grafana, Prometheus y Loki para Frontend... && echo. && docker compose up -d && if errorlevel 1 (echo. && echo [ERROR] No se pudieron iniciar los servicios Docker Frontend && echo [INFO] Verifica que Docker Desktop este ejecutandose && echo. && pause) else (echo. && echo [SUCCESS] Servicios Docker Frontend iniciados correctamente && echo. && echo URLs disponibles: && echo - Frontend Dashboard: http://localhost:8083 && echo - Grafana: http://localhost:3002 (usuario: admin, contraseña: admin123) && echo - Prometheus: http://localhost:9092 && echo - Loki: http://localhost:3102 && echo. && echo Presiona cualquier tecla para cerrar esta ventana... && pause >nul)"

echo [SUCCESS] Comando de Docker Frontend ejecutado en ventana separada
echo [INFO] Los servicios se estan iniciando en segundo plano
echo.
pause
goto MENU

:STOP_DOCKER
echo.
echo [INFO] Deteniendo servicios Docker Frontend...
echo.

start "Stop Docker Frontend Services" cmd /c "echo ========================================== && echo   DETENIENDO SERVICIOS DOCKER FRONTEND && echo ========================================== && echo. && echo [INFO] Deteniendo Grafana, Prometheus y Loki del Frontend... && echo. && docker compose down && if errorlevel 0 (echo. && echo [SUCCESS] Servicios Docker Frontend detenidos correctamente) else (echo. && echo [ERROR] Error al detener los servicios Docker Frontend) && echo. && echo Presiona cualquier tecla para cerrar esta ventana... && pause >nul"

echo [SUCCESS] Comando de parada de Docker Frontend ejecutado en ventana separada
echo [INFO] Los servicios se estan deteniendo en segundo plano
echo.
pause
goto MENU

:OPEN_FRONTEND
echo.
echo [INFO] Abriendo Frontend Dashboard...
echo.

echo [INFO] Ejecutando Frontend Dashboard en navegador...
start "Frontend Dashboard" http://localhost:8083

echo [SUCCESS] Frontend Dashboard abierto en navegador (puerto 8083)
echo [INFO] URLs disponibles:
echo       - Frontend Dashboard: http://localhost:8083
echo       - Para acceso desde red local: http://[TU-IP]:8083
echo.
pause
goto MENU

:OPEN_GRAFANA
echo.
echo [INFO] Abriendo Grafana Dashboard...
echo.

echo [INFO] Ejecutando Grafana en navegador...
start "Grafana Dashboard" http://localhost:3002

echo [SUCCESS] Grafana abierto en navegador (puerto 3002)
echo [INFO] Credenciales: admin / admin123
echo [INFO] URLs disponibles:
echo       - Grafana: http://localhost:3002
echo       - Para acceso desde red local: http://[TU-IP]:3002
echo.
pause
goto MENU

:OPEN_PROMETHEUS
echo.
echo [INFO] Abriendo Prometheus...
echo.

echo [INFO] Ejecutando Prometheus en navegador...
start "Prometheus" http://localhost:9092

echo [SUCCESS] Prometheus abierto en navegador (puerto 9092)
echo [INFO] URLs disponibles:
echo       - Prometheus: http://localhost:9092
echo       - Para acceso desde red local: http://[TU-IP]:9092
echo.
pause
goto MENU



:CHECK_STATUS
echo.
echo [INFO] Verificando estado detallado de los servicios del Frontend...
echo.

echo [INFO] === CONTENEDORES DOCKER ===
docker compose ps

echo.
echo [INFO] === PUERTOS DEL FRONTEND ===
echo [INFO] Verificando puertos 8083, 3002, 9092, 3102...
netstat -an | findstr ":8083" | findstr "LISTENING" >nul && echo [ACTIVE] Puerto 8083 - Frontend Web (Nginx) || echo [INACTIVE] Puerto 8083 - Frontend Web (Nginx)
netstat -an | findstr ":3002" | findstr "LISTENING" >nul && echo [ACTIVE] Puerto 3002 - Grafana Dashboard || echo [INACTIVE] Puerto 3002 - Grafana Dashboard
netstat -an | findstr ":9092" | findstr "LISTENING" >nul && echo [ACTIVE] Puerto 9092 - Prometheus || echo [INACTIVE] Puerto 9092 - Prometheus
netstat -an | findstr ":3102" | findstr "LISTENING" >nul && echo [ACTIVE] Puerto 3102 - Loki || echo [INACTIVE] Puerto 3102 - Loki

echo.
echo [INFO] === SERVICIOS CONFIGURADOS ===
echo       - Frontend Web (Nginx): puerto 8083
echo       - Grafana Dashboard: puerto 3002 (credenciales: admin/admin123)
echo       - Prometheus: puerto 9092
echo       - Loki: puerto 3102

echo.
echo [INFO] === VOLUMENES DOCKER ===
docker volume ls | findstr frontend-

echo.
echo [INFO] === USO DE RECURSOS ===
docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}"

echo.
echo [INFO] === LOGS DE CONTENEDORES ===
echo [INFO] Para ver logs de un servicio especifico:
echo       docker compose logs frontend-web
echo       docker compose logs frontend-grafana  
echo       docker compose logs frontend-prometheus
echo       docker compose logs frontend-loki

echo.
pause
goto MENU

:CLEAN_RESTART
cls
echo.
echo =============================================
echo           LIMPIEZA Y REINICIO
echo =============================================
echo.
echo ⚠️  ADVERTENCIA: Esta acción eliminará:
echo    - Todos los dashboards personalizados de Grafana
echo    - Métricas históricas de Prometheus
echo    - Logs almacenados en Loki
echo    - Configuraciones personalizadas
echo.
set /p confirm=¿Estás seguro? Escribe 'SI' para continuar: 

if /i not "!confirm!"=="SI" (
    echo ❌ Operación cancelada
    pause
    goto MENU
)

echo.
echo [1/4] Deteniendo servicios...
docker compose down

echo [2/4] Eliminando volúmenes...
docker volume rm frontend-grafana-data frontend-prometheus-data frontend-loki-data 2>nul

echo [3/4] Limpiando imágenes no utilizadas...
docker system prune -f >nul 2>&1

echo [4/4] Reiniciando servicios con configuración limpia...
docker compose up -d

if !errorlevel! equ 0 (
    echo ✅ Sistema reiniciado con configuración limpia
    echo.
    echo 🔄 Los dashboards se cargarán automáticamente
    echo 📊 Grafana volverá a las credenciales por defecto: admin/admin123
) else (
    echo ❌ Error durante el reinicio limpio
)

echo.
pause
goto MENU

:EXIT
echo.
echo =============================================
echo              ¡HASTA LUEGO! 👋
echo =============================================
echo.
echo 📋 Resumen de servicios del Frontend:
echo.
echo 🌐 Dashboard Frontend:    http://localhost:8083
echo 📊 Grafana Dashboard:     http://localhost:3002 (admin/admin123)
echo 📈 Prometheus:            http://localhost:9092
echo 📋 Loki:                  http://localhost:3102
echo.
echo 💡 Para acceso desde red local:
echo    Reemplaza 'localhost' con tu IP local
echo    Ejemplo: http://192.168.1.100:8083
echo.
echo 🔗 El Frontend se conecta al API Gateway en puerto 8091
echo    Asegúrate de que esté ejecutándose para métricas completas
echo.
echo 📞 Comandos útiles:
echo    docker compose logs -f    (ver logs)
echo    docker compose ps         (estado)
echo    docker compose down       (detener)
echo.
pause
