# 🎯 Frontend Dashboard - API Gateway Monitor

## 📖 Descripción

Frontend independiente para monitorear el API Gateway y todos los servidores conectados. Incluye stack completo de monitoreo con Grafana, Prometheus y Loki en puertos diferentes al servidor principal.

## 🏗️ Arquitectura

```
Frontend Dashboard (Puerto 8080)
├── 📊 Grafana (Puerto 3001) - Dashboards y métricas comparativas
├── 📈 Prometheus (Puerto 9091) - Scraping de métricas del Gateway
├── 📋 Loki (Puerto 3101) - Aggregación de logs
├── 🔄 Promtail - Recolección de logs
└── 🌐 Nginx - Servidor web para el dashboard
```

## 🚀 Inicio Rápido

### ⚡ Script Único - Todo en Uno
```bash
# Gestor completo con menú interactivo
./run-frontend.bat
```

**El script incluye todas las funciones:**
- 🚀 Iniciar/detener servicios
- 📊 Abrir dashboards automáticamente
- 📋 Ver logs en tiempo real
- 🧹 Limpiar y reiniciar
- 📊 Monitoreo de estado

### Docker Compose Manual (Alternativo)
```bash
# Iniciar servicios
docker compose up -d

# Ver logs
docker compose logs -f

# Detener
docker compose down
```

## 🔗 URLs de Acceso

| Servicio | URL Local | URL Red Local | Diferencia con Servidor |
|----------|-----------|---------------|-------------------------|
| 🎯 **Dashboard Frontend** | http://localhost:8080 | http://[TU-IP]:8080 | Puerto único |
| 📊 **Grafana** | http://localhost:3002 | http://[TU-IP]:3002 | vs 3000 del servidor |
| 📈 **Prometheus** | http://localhost:9092 | http://[TU-IP]:9092 | vs 9090 del servidor |
| 📋 **Loki** | http://localhost:3102 | http://[TU-IP]:3102 | vs 3100 del servidor |

### 🔑 Credenciales por Defecto
- **Grafana**: `admin` / `admin123`

## 📊 Características del Dashboard

### 🎮 Panel Principal
- ✅ Verificación de estado del API Gateway
- 🔌 Conexión WebSocket en tiempo real
- 📡 Lista de servidores conectados con health checks dinámicos
- 🎯 Botón directo a Grafana para métricas comparativas

### 📈 Métricas en Tiempo Real
- **Conexiones por servidor**
- **Tiempo de respuesta**
- **Uso de CPU y memoria**
- **Estado de salud** (ACTIVO/INACTIVO)

### 📋 Logs Centralizados
- Logs históricos al conectar
- Logs incrementales en tiempo real
- Filtrado por servidor y nivel
- Formato normalizado y timestamps

## 🎨 Dashboard de Grafana

### 📊 Paneles Incluidos
1. **Conexiones por Servidor** - Gráfico temporal
2. **Tiempo de Respuesta** - Comparativa entre servidores  
3. **Uso de CPU** - Monitoreo de recursos
4. **Uso de Memoria** - Alertas por thresholds
5. **Logs Centralizados** - Vista unificada de eventos
6. **Resumen del Sistema** - Estadísticas generales
7. **Distribución de Conexiones** - Gráfico de torta
8. **Tabla Comparativa** - Estado detallado por servidor

### ⚙️ Configuración Automática
- 🔄 Refresh cada 5 segundos
- 📅 Ventana de tiempo: últimos 15 minutos
- 🎨 Tema oscuro optimizado
- 🏷️ Tags: api-gateway, monitoring, servers

## 🌐 Acceso desde Red Local

### 📡 Configuración para Múltiples PCs

1. **En el PC que ejecuta el Frontend:**
   ```bash
   # Obtener IP local
   ipconfig
   # Ejemplo: 192.168.1.100
   ```

2. **Desde otros PCs en la misma red:**
   - Dashboard: `http://192.168.1.100:8080`
   - Grafana: `http://192.168.1.100:3001`

3. **Configurar IP del Gateway en el Dashboard:**
   - Hacer clic en el botón ⚙️ junto a la IP del Gateway
   - Ingresar la IP donde corre el API Gateway
   - Ejemplo: `192.168.1.50` (si Gateway está en otro PC)

## ⚡ Configuración Avanzada

### 🔧 Variables de Entorno

Puedes personalizar el `docker-compose.yml`:

```yaml
environment:
  - GF_SECURITY_ADMIN_PASSWORD=tu-password
  - GF_SERVER_ROOT_URL=http://tu-ip:3001
```

### 📂 Volúmenes Persistentes
- `frontend-grafana-data`: Dashboards y configuración
- `frontend-prometheus-data`: Métricas históricas
- `frontend-loki-data`: Logs almacenados

### 🔄 Scraping de Métricas

Prometheus está configurado para obtener métricas de:
- **API Gateway**: `host.docker.internal:8091/gateway/actuator/prometheus`
- **Servidores REST**: `host.docker.internal:8089/actuator/prometheus`
- **Servicios internos**: Grafana, Loki, Promtail

## 🛠️ Troubleshooting

### ❌ Problemas Comunes

1. **Puerto ocupado**
   ```bash
   # Verificar puertos en uso
   netstat -an | findstr "8080\|3001\|9091\|3101"
   
   # Cambiar puertos en docker-compose.yml si es necesario
   ```

2. **Gateway no conecta**
   - Verificar que API Gateway esté corriendo en 8091
   - Configurar IP correcta en el dashboard
   - Revisar firewall/antivirus

3. **Grafana no muestra datos**
   ```bash
   # Verificar logs de Prometheus
   docker compose logs prometheus
   
   # Verificar conexión al Gateway
   curl http://localhost:8091/gateway/actuator/health
   ```

4. **Permisos Docker**
   ```bash
   # En Linux/WSL, asegurar permisos
   sudo chown -R $USER:$USER ./monitoring
   ```

## 📝 Logs y Monitoreo

### 🔍 Ver Logs
```bash
# Todos los servicios
docker compose logs -f

# Servicio específico
docker compose logs -f grafana
docker compose logs -f prometheus
docker compose logs -f frontend
```

### 📊 Métricas de Salud
```bash
# Estado de contenedores
docker compose ps

# Uso de recursos
docker stats
```

## 🚀 Desarrollo y Personalización

### 📝 Modificar Dashboard HTML
El archivo `index.html` contiene toda la lógica del frontend. Cambios se reflejan automáticamente en Nginx.

### 🎨 Personalizar Grafana
1. Acceder a Grafana: http://localhost:3001
2. Modificar dashboards existentes
3. Crear nuevos paneles
4. Los cambios se persisten en el volumen

### 📈 Agregar Nuevas Métricas
1. Modificar `prometheus.yml` para nuevos targets
2. Crear alertas en Grafana
3. Actualizar dashboards con nuevos paneles

## 📞 Soporte

Para problemas o mejoras:
1. Revisar logs con `docker compose logs -f`
2. Verificar conectividad de red
3. Comprobar configuración de IPs
4. Reiniciar servicios: `docker compose restart`