# REST API - Servidor TCP

## Descripción
API REST que complementa el servidor TCP principal, proporcionando endpoints para acceder a métricas de Prometheus y logs del sistema con documentación Swagger integrada.

## Configuración

### Puerto
La API REST está configurada para ejecutarse en el puerto **8089** (configurable en `application.properties`).

### Base de datos
Utiliza la misma base de datos MySQL que el servidor TCP principal.

## Documentación Swagger

La API incluye documentación interactiva con Swagger/OpenAPI 3:

### URLs de Swagger
- **Swagger UI**: http://localhost:8089/swagger-ui.html
- **API Docs JSON**: http://localhost:8089/api-docs

### Características de la documentación
- Interfaz web interactiva para probar endpoints
- Documentación detallada de cada endpoint
- Ejemplos de respuestas y esquemas de datos
- Agrupación por categorías (Métricas y Logs)

## Endpoints Disponibles

### 1. Métricas de Prometheus

**GET** `/api/metrics`

Devuelve todas las métricas del servidor en formato Prometheus (mismo formato que expone el endpoint de métricas original).

**Ejemplo de uso:**
```bash
curl http://localhost:8089/api/metrics
```

**Respuesta:** Texto plano en formato Prometheus con todas las métricas:
- `chat_tcp_active_connections`: Conexiones TCP activas
- `chat_commands_total`: Comandos procesados por tipo y resultado
- `chat_login_attempts_total`: Intentos de autenticación
- `chat_realtime_events_total`: Eventos de tiempo real
- `chat_p2p_connected_peers`: Peers P2P conectados
- `chat_system_cpu_usage_percent`: Uso de CPU
- `chat_system_memory_usage_percent`: Uso de memoria
- Y muchas más... (ver `Grafana.md` para lista completa)

**Endpoint de salud:**
```bash
curl http://localhost:8089/api/metrics/health
```

### 2. Logs del Servidor

**GET** `/api/logs`

Devuelve todos los logs registrados en la base de datos en formato JSON.

**Ejemplo de uso:**
```bash
curl http://localhost:8089/api/logs
```

**Respuesta:** Array JSON con los logs:
```json
[
  {
    "id": 1,
    "tipo": "INFO",
    "detalle": "Servidor iniciado exitosamente",
    "fechaHora": "2025-11-24T15:30:00"
  },
  {
    "id": 2,
    "tipo": "ERROR",
    "detalle": "Error de conexión",
    "fechaHora": "2025-11-24T15:35:00"
  }
]
```

**Endpoint de salud:**
```bash
curl http://localhost:8089/api/logs/health
```

### 3. Actuator Endpoints (Spring Boot)

Spring Boot Actuator proporciona endpoints adicionales de monitoreo:

- **GET** `/actuator/health` - Estado de salud de la aplicación
- **GET** `/actuator/info` - Información de la aplicación
- **GET** `/actuator/prometheus` - Métricas en formato Prometheus (alternativa a `/api/metrics`)

**Ejemplo:**
```bash
curl http://localhost:8089/actuator/health
```

## Uso con Swagger

### 1. Iniciar la aplicación
La REST API se inicia automáticamente junto con el servidor TCP principal.

### 2. Acceder a Swagger UI
Abre tu navegador y ve a: **http://localhost:8089/swagger-ui.html**

### 3. Explorar la documentación
- Utiliza la interfaz de Swagger para ver la documentación completa
- Prueba los endpoints directamente desde la interfaz web
- Ve ejemplos de respuestas y esquemas de datos
- Descarga la especificación OpenAPI desde `/api-docs`

## Integración con Grafana

La API REST puede ser utilizada como fuente de datos en Grafana:

1. **Opción 1:** Configurar Grafana para leer de: http://localhost:8089/api/metrics
2. **Opción 2:** Usar el endpoint: http://localhost:8089/actuator/prometheus

Ambos endpoints exponen las mismas métricas en formato compatible con Prometheus/Grafana.

## Configuración

### Puerto de la API
El puerto se configura **únicamente** en:
`Aplicacion/RestAPI/src/main/resources/application.properties`

```properties
server.port=8089
```

**Nota importante:** Ya no es necesario configurar `rest.api.port` en `server.properties`. Solo se usa el `application.properties` del módulo RestAPI.

### Personalizar Swagger
La configuración de Swagger se encuentra en `SwaggerConfig.java` y puede ser personalizada según las necesidades del proyecto.
