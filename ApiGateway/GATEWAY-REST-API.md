# 🌐 API Gateway - Conexión a Servidores vía REST

## 📋 **Descripción**
El API Gateway se conecta a **servidores mediante sus APIs REST** expuestas. Cada servidor expone una API REST que el gateway consume para obtener datos, métricas y logs. No hay comunicación TCP directa.

## 🔗 **Arquitectura de Conexión**

```
Frontend → API Gateway (Puerto 8091) → Servidor API REST (Puerto X)
                ↓
            [WebSocket]     →     [HTTP REST Calls]
                ↓                        ↓
         Tiempo real              Datos/Métricas
```

## 🚀 **Endpoints del API Gateway**

### **Gestión de Conexiones a Servidores**

#### 1. **Conectar Servidor TCP**
```http
POST /gateway/api/servers/connect
Content-Type: application/json

{
    "host": "192.168.1.100",
    "port": 8080
}
```

**Respuesta Exitosa:**
```json
{
    "success": true,
    "message": "Servidor conectado exitosamente",
    "server": {
        "id": "192.168.1.100:8080",
        "host": "192.168.1.100",
        "restApiPort": 8080,
        "status": "CONNECTED",
        "connectedAt": "2024-01-01T10:00:00Z"
    }
}
```

#### 2. **Desconectar Servidor TCP**
```http
DELETE /gateway/api/servers/disconnect
Content-Type: application/json

{
    "host": "192.168.1.100",
    "port": 8080
}
```

#### 3. **Listar Servidores Conectados**
```http
GET /gateway/api/servers
```

**Respuesta:**
```json
{
    "success": true,
    "servers": [
        {
            "id": "192.168.1.100:8080",
            "host": "192.168.1.100",
            "restApiPort": 8080,
            "status": "CONNECTED"
        }
    ],
    "stats": {
        "totalServers": 3,
        "connectedServers": 2,
        "errorServers": 1,
        "lastCheck": "2024-01-01T10:05:00Z"
    }
}
```

#### 4. **Health Check Manual**
```http
POST /gateway/api/servers/health-check
```

## 🔍 **¿Qué hace el Gateway con cada Servidor?**

### **Health Checks Automáticos**
El gateway verifica la salud de cada servidor TCP cada 30 segundos:
```http
GET http://[host]:[port]/actuator/health
```

### **Conexiones WebSocket**
Una vez verificado que el servidor está saludable, el gateway establece conexiones WebSocket para recibir datos en tiempo real:
```
ws://[host]:[port]/ws/metrics
ws://[host]:[port]/ws/logs
```

### **Agregación de Datos**
- **Métricas**: CPU, memoria, conexiones activas, etc.
- **Logs**: Mensajes de aplicación, errores, warnings
- **Estado**: Conectado, desconectado, error

## 📊 **Frontend Integration**

### **WebSocket del Gateway (Para Frontends)**
Los frontends se conectan al gateway para recibir datos agregados:

```javascript
// Conectar al gateway
const socket = new SockJS('http://localhost:8091/gateway/ws');
const stompClient = Stomp.over(socket);

// Suscribirse a métricas agregadas
stompClient.subscribe('/topic/metrics', (message) => {
    const metrics = JSON.parse(message.body);
    console.log('Métricas de todos los servidores:', metrics);
});

// Suscribirse a logs agregados
stompClient.subscribe('/topic/logs', (message) => {
    const logs = JSON.parse(message.body);
    console.log('Logs de todos los servidores:', logs);
});
```

## 🛠️ **Configuración Requerida en Servidores TCP**

Para que el gateway pueda conectarse, cada servidor TCP debe exponer:

### **1. Health Check Endpoint**
```http
GET /actuator/health
```

### **2. WebSocket Endpoints**
```
/ws/metrics - Para métricas en tiempo real
/ws/logs    - Para logs en tiempo real
```

### **3. CORS Configuration**
Permitir conexiones desde el gateway:
```properties
# En application.properties del servidor TCP
management.endpoints.web.cors.allowed-origins=http://localhost:8091
```

## ⚙️ **URLs del Gateway**

Una vez iniciado el API Gateway:

- **Swagger UI**: http://localhost:8091/gateway/swagger-ui.html
- **Health Check**: http://localhost:8091/gateway/actuator/health
- **API Docs**: http://localhost:8091/gateway/api-docs
- **WebSocket**: ws://localhost:8091/gateway/ws

## 🔥 **Ejemplo de Uso Completo**

### **Paso 1**: Iniciar API Gateway
```bash
cd ApiGateway
mvn spring-boot:run
```

### **Paso 2**: Conectar Servidor TCP
```bash
curl -X POST http://localhost:8091/gateway/api/servers/connect \
  -H "Content-Type: application/json" \
  -d '{
    "host": "192.168.1.100",
    "port": 8080
  }'
```

### **Paso 3**: Frontend se conecta al Gateway
```javascript
const socket = new SockJS('http://localhost:8091/gateway/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, () => {
    // Recibir datos agregados de TODOS los servidores
    stompClient.subscribe('/topic/metrics', handleMetrics);
    stompClient.subscribe('/topic/logs', handleLogs);
});
```

## 🎯 **Beneficios de esta Arquitectura**

✅ **Escalabilidad**: Conectar múltiples servidores dinámicamente  
✅ **Agregación**: Un solo punto para recibir datos de todos los servidores  
✅ **Monitoreo**: Health checks automáticos  
✅ **Tiempo Real**: WebSockets para datos en vivo  
✅ **REST API**: Gestión sencilla de conexiones  
✅ **Frontend Único**: Un solo endpoint para el frontend