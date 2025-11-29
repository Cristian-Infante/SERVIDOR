# 📡 TCP Servers API Gateway - Guía Frontend

## 🎯 Propósito
API Gateway para conectar múltiples servidores TCP y agregar sus métricas/logs en tiempo real. Proporciona endpoints REST y WebSockets para frontends que necesitan monitorear múltiples servidores desde una sola interfaz.

## 🚀 URLs de Acceso

> **⚠️ IMPORTANTE**: El API Gateway se ejecuta **independientemente** de Docker Compose. Los servicios de monitoreo (Prometheus, Grafana, Loki) se ejecutan en Docker.

### 📊 Swagger UI (Documentación Interactiva)
```
http://localhost:8090/gateway/swagger-ui.html
```

### 🔗 Endpoints Base
```
REST API: http://localhost:8090/gateway/api/
WebSocket: ws://localhost:8090/gateway/ws/
```

### 🏃‍♂️ Como Iniciar el Sistema
```bash
# 1. Servicios de monitoreo (Docker)
.\start-gateway-system.bat

# 2. API Gateway (Independiente)
.\start-api-gateway.bat
```

---

## 📋 REST API Endpoints

### 🔌 Gestión de Servidores

#### ✅ Conectar Servidor
```http
POST /api/servers/connect
Content-Type: application/json

{
    "host": "192.168.1.100",
    "port": 8080,
    "region": "production"
}
```

**Respuesta:**
```json
{
    "success": true,
    "message": "Servidor conectado exitosamente",
    "server": {
        "id": "srv_192_168_1_100_8080",
        "host": "192.168.1.100",
        "port": 8080,
        "region": "production",
        "status": "CONNECTED",
        "connectedAt": "2024-01-01T10:00:00Z"
    }
}
```

#### ❌ Desconectar Servidor
```http
DELETE /api/servers/disconnect
Content-Type: application/json

{
    "host": "192.168.1.100",
    "port": 8080
}
```

#### 📋 Listar Servidores Activos
```http
GET /api/servers
```

**Respuesta:**
```json
{
    "success": true,
    "servers": [...],
    "stats": {
        "totalServers": 3,
        "healthyServers": 2,
        "totalConnections": 150
    }
}
```

### 📊 Consulta de Datos

#### 🔢 Métricas Agregadas
```http
GET /api/data/metrics
```

**Respuesta:**
```json
{
    "success": true,
    "metrics": {
        "totalServers": 3,
        "totalConnections": 150,
        "avgResponseTime": 45.2,
        "totalErrors": 5,
        "memoryUsage": 67.8,
        "cpuUsage": 23.5,
        "aggregatedAt": "2024-01-01T10:00:00"
    }
}
```

#### 📝 Logs Agregados
```http
GET /api/data/logs?level=ERROR&limit=50
```

**Parámetros de Query:**
- `level` (opcional): INFO, WARN, ERROR
- `serverId` (opcional): ID específico del servidor
- `limit` (opcional): Número máximo de logs (default: 100)

---

## ⚡ WebSockets en Tiempo Real

### 🔗 Conexión WebSocket

```javascript
// Usando SockJS + STOMP
const socket = new SockJS('http://localhost:8090/gateway/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('Conectado: ' + frame);
    
    // Suscribirse a métricas
    stompClient.subscribe('/topic/metrics', function(message) {
        const data = JSON.parse(message.body);
        console.log('Métricas recibidas:', data);
    });
    
    // Suscribirse a logs
    stompClient.subscribe('/topic/logs', function(message) {
        const data = JSON.parse(message.body);
        console.log('Logs recibidos:', data);
    });
});
```

### 📡 Endpoints WebSocket Disponibles

| Endpoint | Descripción | Datos |
|----------|-------------|-------|
| `/topic/metrics` | Métricas agregadas actualizadas | Objeto con métricas consolidadas |
| `/topic/logs` | Nuevos logs agregados | Array de logs recientes |
| `/topic/servers` | Notificaciones de conexión/desconexión | Estado de servidores |

### 📨 Estructura de Mensajes WebSocket

#### Métricas:
```json
{
    "type": "metrics-update",
    "data": {
        "totalServers": 3,
        "totalConnections": 150,
        "avgResponseTime": 45.2
    },
    "timestamp": "2024-01-01T10:00:00"
}
```

#### Logs:
```json
{
    "type": "logs-update",
    "data": [
        {
            "server": "srv_192_168_1_100_8080",
            "level": "ERROR",
            "message": "Conexión perdida",
            "timestamp": "2024-01-01T10:00:00"
        }
    ],
    "count": 1,
    "timestamp": "2024-01-01T10:00:00"
}
```

#### Notificaciones de Servidores:
```json
{
    "type": "server-connected",
    "serverId": "srv_192_168_1_100_8080",
    "host": "192.168.1.100",
    "port": 8080,
    "timestamp": "2024-01-01T10:00:00"
}
```

---

## 🛠️ Implementación Frontend

### 📋 Ejemplo Completo JavaScript

```javascript
class TcpGatewayClient {
    constructor(baseUrl = 'http://localhost:8090/gateway') {
        this.baseUrl = baseUrl;
        this.socket = null;
        this.stompClient = null;
    }
    
    // Conectar WebSocket
    connectWebSocket() {
        this.socket = new SockJS(`${this.baseUrl}/ws`);
        this.stompClient = Stomp.over(this.socket);
        
        this.stompClient.connect({}, (frame) => {
            console.log('WebSocket conectado');
            this.subscribeToUpdates();
        });
    }
    
    // Suscribirse a actualizaciones
    subscribeToUpdates() {
        // Métricas en tiempo real
        this.stompClient.subscribe('/topic/metrics', (message) => {
            const data = JSON.parse(message.body);
            this.onMetricsUpdate(data);
        });
        
        // Logs en tiempo real
        this.stompClient.subscribe('/topic/logs', (message) => {
            const data = JSON.parse(message.body);
            this.onLogsUpdate(data);
        });
        
        // Notificaciones de servidores
        this.stompClient.subscribe('/topic/servers', (message) => {
            const data = JSON.parse(message.body);
            this.onServerNotification(data);
        });
    }
    
    // Conectar servidor
    async connectServer(host, port, region = 'default') {
        const response = await fetch(`${this.baseUrl}/api/servers/connect`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ host, port, region })
        });
        return await response.json();
    }
    
    // Obtener servidores activos
    async getServers() {
        const response = await fetch(`${this.baseUrl}/api/servers`);
        return await response.json();
    }
    
    // Callbacks (implementar en tu frontend)
    onMetricsUpdate(data) {
        console.log('Nuevas métricas:', data);
        // Actualizar dashboard, gráficos, etc.
    }
    
    onLogsUpdate(data) {
        console.log('Nuevos logs:', data);
        // Agregar a tabla de logs, mostrar alertas, etc.
    }
    
    onServerNotification(data) {
        console.log('Notificación servidor:', data);
        // Actualizar lista de servidores conectados
    }
}

// Uso
const gateway = new TcpGatewayClient();
gateway.connectWebSocket();

// Conectar servidor
gateway.connectServer('192.168.1.100', 8080, 'production')
    .then(result => console.log('Servidor conectado:', result));
```

### 🎨 Ejemplo React Hook

```javascript
import { useState, useEffect, useRef } from 'react';
import SockJS from 'sockjs-client';
import { Stomp } from '@stomp/stompjs';

export const useTcpGateway = (baseUrl = 'http://localhost:8090/gateway') => {
    const [servers, setServers] = useState([]);
    const [metrics, setMetrics] = useState({});
    const [logs, setLogs] = useState([]);
    const [connected, setConnected] = useState(false);
    
    const stompClient = useRef(null);
    
    useEffect(() => {
        // Conectar WebSocket
        const socket = new SockJS(`${baseUrl}/ws`);
        stompClient.current = Stomp.over(socket);
        
        stompClient.current.connect({}, () => {
            setConnected(true);
            
            // Suscribirse a actualizaciones
            stompClient.current.subscribe('/topic/metrics', (message) => {
                const data = JSON.parse(message.body);
                setMetrics(data.data);
            });
            
            stompClient.current.subscribe('/topic/logs', (message) => {
                const data = JSON.parse(message.body);
                setLogs(prev => [...data.data, ...prev].slice(0, 100));
            });
            
            stompClient.current.subscribe('/topic/servers', (message) => {
                const data = JSON.parse(message.body);
                if (data.type === 'server-connected') {
                    loadServers(); // Recargar lista
                }
            });
        });
        
        return () => {
            if (stompClient.current) {
                stompClient.current.disconnect();
            }
        };
    }, [baseUrl]);
    
    const loadServers = async () => {
        try {
            const response = await fetch(`${baseUrl}/api/servers`);
            const data = await response.json();
            setServers(data.servers);
        } catch (error) {
            console.error('Error cargando servidores:', error);
        }
    };
    
    const connectServer = async (host, port, region) => {
        try {
            const response = await fetch(`${baseUrl}/api/servers/connect`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ host, port, region })
            });
            const result = await response.json();
            if (result.success) {
                loadServers();
            }
            return result;
        } catch (error) {
            console.error('Error conectando servidor:', error);
            return { success: false, error: error.message };
        }
    };
    
    return {
        servers,
        metrics,
        logs,
        connected,
        connectServer,
        loadServers
    };
};
```

---

## 🐋 Deployment del Sistema

### 🚀 Opción 1: Scripts Automatizados
```bash
# Iniciar servicios de monitoreo + compilar gateway
.\start-gateway-system.bat

# En otra terminal: Iniciar API Gateway
.\start-api-gateway.bat
```

### 🔧 Opción 2: Manual
```bash
# 1. Servicios de monitoreo (Docker)
docker-compose up -d

# 2. Compilar API Gateway
cd ApiGateway
mvn clean package -DskipTests

# 3. Ejecutar API Gateway
mvn spring-boot:run
```

### 🌐 URLs después del deployment
- **API Gateway:** http://localhost:8090/gateway/
- **Swagger UI:** http://localhost:8090/gateway/swagger-ui.html
- **Grafana:** http://localhost:3001 (admin/admin123)
- **Prometheus:** http://localhost:9090
- **Demo Frontend:** Abrir `frontend-demo.html` en navegador

---

## 🔧 Configuración Personalizada

### Variables de Entorno
```bash
# Puerto del gateway
SERVER_PORT=8090

# Perfil Spring
SPRING_PROFILES_ACTIVE=docker

# Memoria JVM
JAVA_OPTS=-Xmx512m -Xms256m
```

### 🏥 Health Check
```http
GET /actuator/health
```

### 📊 Métricas Prometheus
```http
GET /actuator/prometheus
```

---

## ⚠️ Consideraciones Importantes

1. **CORS:** Configurado para aceptar cualquier origen (`*`)
2. **WebSocket:** Usa SockJS para compatibilidad con navegadores
3. **Rate Limiting:** No implementado (agregar según necesidades)
4. **Autenticación:** No implementada (agregar JWT según necesidades)
5. **SSL:** No configurado (usar proxy reverso para HTTPS en producción)

## 📞 Soporte

Para dudas sobre la implementación, revisa:
- **Swagger UI:** Documentación interactiva completa
- **Logs:** `docker-compose logs -f api-gateway`
- **Health:** `/actuator/health` para verificar estado