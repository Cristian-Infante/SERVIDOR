# 📝 Configuración de Loki para Logs

## 🚀 Inicio Rápido

### 1. Iniciar el stack de monitoreo
```bash
.\run-system.bat
```
Selecciona la opción **1** (Iniciar TODO) o **2** (Solo servicios Docker)

### 2. Verificar logs en Grafana
- Abrir http://localhost:3000
- Usuario: `admin`, Contraseña: `admin123`
- Ir a "Explore" → seleccionar datasource "Loki" (ya configurado automáticamente)

## 🔍 Consultas útiles en Loki (LogQL)

### Ver todos los logs del servidor
```logql
{server="server-a"}
```

### Logs por nivel
```logql
{server="server-a", level="ERROR"}
{server="server-a", level="INFO"}
```

### Logs de la REST API
```logql
{service="rest-api"}
```

### Logs de conexiones TCP
```logql
{server="server-a"} |= "CONNECTION"
```

### Logs de métricas
```logql
{logger="com.arquitectura.servicios.metrics"}
```

### Búsqueda por texto
```logql
{server="server-a"} |= "login" |= "usuario"
```

### Logs de errores en la última hora
```logql
{server="server-a", level="ERROR"} [1h]
```

### Logs con filtros combinados
```logql
{server="server-a"} |= "TCP" != "DEBUG" | json
```

## 🔧 Configuración

### URLs por defecto:
- **Loki:** http://localhost:3100
- **Grafana:** http://localhost:3000 (admin/admin123)
- **Prometheus:** http://localhost:9090
- **Servidor Chat:** http://localhost:5000
- **REST API:** http://localhost:8089

### Estructura de archivos:
```
Servidor/
├── config/
│   ├── loki-config.yml          # Configuración de Loki
│   ├── prometheus.yml           # Configuración de Prometheus
│   └── grafana/
│       └── provisioning/
│           └── datasources/
│               └── datasources.yml  # Auto-configuración de Grafana
├── docker-compose-monitoring.yml   # Stack completo
├── start-monitoring.bat           # Script de inicio
└── stop-monitoring.bat            # Script de parada
```

### Archivos de configuración de logs:
- **Bootstrap:** `Aplicacion/Bootstrap/src/main/resources/logback.xml`
- **RestAPI:** `Aplicacion/RestAPI/src/main/resources/logback-spring.xml`

## 📊 Integración con Grafana

### Datasources configurados automáticamente:
- **Loki** (por defecto): Para logs y búsquedas
- **Prometheus**: Para métricas numéricas

### Crear dashboards:
1. En Grafana, ve a "+" → "Dashboard"
2. Agrega paneles con consultas de Loki
3. Combina con métricas de Prometheus

### Ejemplos de paneles:
- **Logs en tiempo real:** `{server="server-a"}` con refresh automático
- **Errores por hora:** `sum(count_over_time({level="ERROR"}[1h]))`
- **Actividad por servicio:** `{server="server-a"} |= "TCP"`

## 🛠️ Comandos útiles

### Verificar servicios
```bash
docker-compose -f docker-compose-monitoring.yml ps
```

### Ver logs de los contenedores
```bash
docker logs loki
docker logs grafana
docker logs prometheus
```

### Reiniciar servicios
```bash
docker-compose -f docker-compose-monitoring.yml restart
```

### Limpiar volúmenes (cuidado: borra datos)
```bash
docker-compose -f docker-compose-monitoring.yml down -v
```

## 🔍 Troubleshooting

### Si no aparecen logs en Loki:
1. Verificar que Loki esté corriendo: http://localhost:3100/ready
2. Verificar configuración en `logback.xml`
3. Revisar logs del contenedor: `docker logs loki`
4. Verificar que la URL de Loki sea correcta en `server.properties`

### Si Grafana no muestra el datasource:
1. Verificar que el archivo `datasources.yml` esté en el lugar correcto
2. Reiniciar Grafana: `docker restart grafana`
3. Configurar manualmente en Grafana UI

### Si los logs no se envían:
1. Verificar que la dependencia `loki-logback-appender` esté en el classpath
2. Verificar la URL de Loki en las variables de entorno
3. Revisar el archivo `logback.xml` para errores de sintaxis

## 🎯 Próximos pasos

1. **Crear alertas** en Grafana basadas en logs de errores
2. **Configurar retención** de logs en Loki
3. **Optimizar consultas** LogQL para mejor rendimiento
4. **Agregar más labels** para mejor organización de logs