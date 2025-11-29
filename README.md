# Sistema de Chat con Monitoreo

Sistema de chat distribuido con Interface de Vistas y stack de monitoreo (Prometheus + Grafana).

## 🚀 Inicio Rápido

### Prerrequisitos
- Java 21
- Maven 3.8+
- Docker Desktop
- MySQL Server (local)

### Compilar y Ejecutar

1. **Compilar el proyecto**
```bash
mvn clean package -DskipTests
```

2. **Ejecutar todo el sistema**
```bash
run-system.bat
```

¡Eso es todo! El script `run-system.bat` maneja todo automáticamente.

## 🖥️ Script Unificado: `run-system.bat`

### Opciones disponibles:

1. **Iniciar TODO** - Docker + Interface de Vistas
   - Inicia Prometheus y Grafana en contenedores
   - Abre la Interface de Usuario en ventana separada

2. **Solo Docker** - Prometheus + Grafana únicamente
   - Para desarrollo o uso de APIs externas

3. **Solo Interface** - Aplicación de Vistas únicamente
   - Para usar sin monitoreo

4. **Detener Docker** - Parar servicios de contenedores

5. **Estado detallado** - Verificar todos los servicios

6. **Salir** - Con opción de limpiar servicios Docker

## 📊 URLs de Acceso

| Servicio | URL | Credenciales |
|----------|-----|-------------|
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3001 | admin/admin123 |

## 🏗️ Arquitectura

### Servicios en Docker
- **Prometheus**: Puerto 9090 - Recolección de métricas
- **Grafana**: Puerto 3001 - Visualización y dashboards

### Aplicación Java Local
- **Interface de Vistas**: Aplicación de escritorio para gestión del chat
- Se ejecuta con todas las dependencias incluidas

## 📁 Configuración de Base de Datos

```
Host: localhost:3306
Database: databasemensajeria
Username: root
Password: root
```

## 🔧 Solución de Problemas

### El script no funciona
1. Verifica que Docker Desktop esté ejecutándose
2. Compila el proyecto: `mvn clean package -DskipTests`
3. Ejecuta como administrador si es necesario

### Servicios no se conectan
1. Verifica que MySQL esté ejecutándose
2. Confirma la configuración de base de datos
3. Revisa que no hay conflictos de puertos

## 📝 Estructura Simplificada

```
Servidor/
├── run-system.bat          # ← SCRIPT ÚNICO PARA TODO
├── docker-compose.yml      # Configuración de Docker
├── monitoring/             # Config Prometheus/Grafana
├── Presentacion/Vistas/    # Interface de Usuario
└── [otros módulos...]      # Resto del proyecto
```

## ⚡ Comandos Rápidos

```bash
# Iniciar todo
run-system.bat

# Solo compilar
mvn clean package -DskipTests

# Solo Docker (manual)
docker-compose up -d

# Parar Docker (manual)
docker-compose down
```

¡Un solo archivo `.bat` controla todo el sistema!