# Protocolo de comunicación del servidor

Este servidor opera sobre sockets TCP y utiliza **JSON codificado en UTF-8** como formato de intercambio. Cada mensaje corresponde a un objeto JSON serializado en una sola línea.

## Estructura de mensajes

```json
{
  "command": "string",
  "payload": "object | array | string | null"
}
```

## Autenticación

- Comandos que **NO** requieren autenticación: `REGISTER`, `LOGIN`, `PING`
- Todos los demás comandos **requieren autenticación**
- `LOGOUT`: Cierra sesión pero mantiene la conexión TCP abierta
- `CLOSE_CONN`: Cierra sesión y termina la conexión TCP

## Comandos

### `REGISTER`
**Request:**
```json
{
  "command": "REGISTER",
  "payload": {
    "usuario": "string",
    "email": "string",
    "contrasenia": "string",
    "fotoBase64": "string (opcional)",
    "ip": "string"
  }
}
```
**Response:**
```json
{
  "command": "REGISTER",
  "payload": {
    "message": "Registro exitoso. Por favor inicia sesión."
  }
}
```

### `LOGIN`
**Request:**
```json
{
  "command": "LOGIN",
  "payload": {
    "email": "string",
    "contrasenia": "string",
    "ip": "string (opcional)"
  }
}
```
**Response:**
```json
{
  "command": "LOGIN",
  "payload": {
    "message": "Login exitoso"
  }
}
```

**Después del login exitoso**, el servidor envía automáticamente:
```json
{
  "command": "MESSAGE_SYNC",
  "payload": {
    "mensajes": [ /* todos los mensajes del usuario */ ],
    "totalMensajes": 25,
    "ultimaSincronizacion": "2025-10-16T11:30:00"
  }
}
```

**Sesión única**: Si el usuario ya tiene una sesión activa, el nuevo intento será rechazado con error.

### `LOGOUT`
**Request:**
```json
{
  "command": "LOGOUT",
  "payload": null
}
```
**Response:**
```json
{
  "command": "LOGOUT",
  "payload": {
    "message": "Sesión cerrada exitosamente"
  }
}
```

### `PING`
**Request:**
```json
{
  "command": "PING",
  "payload": null
}
```
**Response:**
```json
{
  "command": "PING",
  "payload": {
    "message": "PONG"
  }
}
```

### `UPLOAD_AUDIO`
**Descripción:** Sube un archivo de audio al servidor antes de enviarlo como mensaje.

**Request:**
```json
{
  "command": "UPLOAD_AUDIO",
  "payload": {
    "audioBase64": "string (contenido del audio codificado en Base64)",
    "mime": "audio/wav | audio/mpeg | audio/ogg | etc.",
    "duracionSeg": 15,
    "nombreArchivo": "string (opcional)"
  }
}
```
**Response:**
```json
{
  "command": "UPLOAD_AUDIO",
  "payload": {
    "exito": true,
    "rutaArchivo": "media/audio/usuarios/1/rec_1760597378319.wav",
    "mensaje": "Audio guardado exitosamente"
  }
}
```

**Notas:**
- El servidor guarda el audio en `media/audio/usuarios/<userId>/`
- La `rutaArchivo` devuelta se usa luego en `SEND_USER` o `SEND_CHANNEL`
- Para transcripción óptima, usa formato **WAV 16kHz mono**
- Convierte con FFmpeg: `ffmpeg -i audio.mp3 -ar 16000 -ac 1 audio.wav`

### `SEND_USER`
**Descripción:** Envía un mensaje a un usuario. Para mensajes de audio, primero usa `UPLOAD_AUDIO`.

**Request:**
```json
{
  "command": "SEND_USER",
  "payload": {
    "tipo": "TEXTO | AUDIO | ARCHIVO",
    "contenido": "string (solo TEXTO)",
    "rutaArchivo": "string (AUDIO/ARCHIVO - obtenida de UPLOAD_AUDIO)",
    "mime": "string (AUDIO/ARCHIVO)",
    "duracionSeg": 123,
    "receptor": 42
  }
}
```
**Response:**
```json
{
  "command": "SEND_USER",
  "payload": {
    "message": "Mensaje enviado"
  }
}
```

**Flujo para mensajes de audio:**
1. Codificar audio en Base64
2. Enviar `UPLOAD_AUDIO` con el audio
3. Recibir `rutaArchivo` en la respuesta
4. Enviar `SEND_USER` con la `rutaArchivo` recibida
5. El servidor transcribe automáticamente el audio (si está en formato WAV 16kHz mono)

**Evento recibido por el destinatario:**
```json
{
  "command": "EVENT",
  "payload": {
    "id": 123,
    "tipo": "AUDIO",
    "emisor": 1,
    "receptor": 2,
    "timeStamp": "2025-10-16T14:30:00",
    "rutaArchivo": "media/audio/usuarios/1/rec_1760597378319.wav",
    "mime": "audio/wav",
    "duracionSeg": 15,
    "transcripcion": "hola cómo estás me gustaría coordinar una reunión"
  }
}
```

### `SEND_CHANNEL`
**Descripción:** Envía un mensaje a un canal. Para mensajes de audio, primero usa `UPLOAD_AUDIO`.

**Request:**
```json
{
  "command": "SEND_CHANNEL",
  "payload": {
    "tipo": "TEXTO | AUDIO | ARCHIVO",
    "contenido": "string (solo TEXTO)",
    "rutaArchivo": "string (AUDIO/ARCHIVO - obtenida de UPLOAD_AUDIO)",
    "mime": "string (AUDIO/ARCHIVO)",
    "duracionSeg": 123,
    "canalId": 99
  }
}
```
**Response:**
```json
{
  "command": "SEND_CHANNEL",
  "payload": {
    "message": "Mensaje a canal enviado"
  }
}
```

**Notas:**
- Mismo flujo que `SEND_USER` para mensajes de audio
- Todos los miembros del canal reciben el mensaje con transcripción incluida

### `CREATE_CHANNEL`
**Request:**
```json
{
  "command": "CREATE_CHANNEL",
  "payload": {
    "nombre": "string",
    "privado": true
  }
}
```
**Response:**
```json
{
  "command": "CREATE_CHANNEL",
  "payload": {
    "id": 99,
    "nombre": "general",
    "privado": false
  }
}
```

### `INVITE`
**Request:**
```json
{
  "command": "INVITE",
  "payload": {
    "canalId": 99,
    "invitadoId": 42
  }
}
```
**Response:**
```json
{
  "command": "INVITE",
  "payload": {
    "message": "Invitación enviada"
  }
}
```

### `ACCEPT`
**Request:**
```json
{
  "command": "ACCEPT",
  "payload": {
    "canalId": 99
  }
}
```
**Response:**
```json
{
  "command": "ACCEPT",
  "payload": {
    "message": "Canal aceptado"
  }
}
```

### `REJECT`
**Request:**
```json
{
  "command": "REJECT",
  "payload": {
    "canalId": 99
  }
}
```
**Response:**
```json
{
  "command": "REJECT",
  "payload": {
    "message": "Invitación rechazada"
  }
}
```

### `LIST_RECEIVED_INVITATIONS`
Muestra las invitaciones recibidas (que puedes aceptar o rechazar).

**Request:**
```json
{
  "command": "LIST_RECEIVED_INVITATIONS",
  "payload": null
}
```
**Response:**
```json
{
  "command": "LIST_RECEIVED_INVITATIONS",
  "payload": [
    {
      "canalId": 99,
      "canalNombre": "desarrollo",
      "canalPrivado": true,
      "invitadorId": 42,
      "invitadorNombre": "alice"
    }
  ]
}
```

### `LIST_SENT_INVITATIONS`
Muestra las invitaciones que has enviado y su estado actual.

**Request:**
```json
{
  "command": "LIST_SENT_INVITATIONS",
  "payload": null
}
```
**Response:**
```json
{
  "command": "LIST_SENT_INVITATIONS",
  "payload": [
    {
      "canalId": 99,
      "canalNombre": "desarrollo",
      "canalPrivado": true,
      "invitadoId": 55,
      "invitadoNombre": "bob",
      "estado": "PENDIENTE"
    }
  ]
}
```

### `LIST_USERS`
**Request:**
```json
{
  "command": "LIST_USERS",
  "payload": null
}
```
**Response:**
```json
{
  "command": "LIST_USERS",
  "payload": [
    {
      "id": 1,
      "usuario": "alice",
      "email": "alice@example.com",
      "conectado": true
    }
  ]
}
```

### `LIST_CHANNELS`
**Request:**
```json
{
  "command": "LIST_CHANNELS",
  "payload": null
}
```
**Response:**
```json
{
  "command": "LIST_CHANNELS",
  "payload": [
    {
      "id": 10,
      "nombre": "general",
      "privado": false,
      "usuarios": [...]
    }
  ]
}
```

### `LIST_CONNECTED`
**Request:**
```json
{
  "command": "LIST_CONNECTED",
  "payload": null
}
```
**Response:**
```json
{
  "command": "LIST_CONNECTED",
  "payload": [
    {
      "id": 1,
      "usuario": "alice",
      "email": "alice@example.com",
      "conectado": true
    }
  ]
}
```

### `BROADCAST`
**Request:**
```json
{
  "command": "BROADCAST",
  "payload": {
    "message": "string"
  }
}
```
**Response:**
```json
{
  "command": "BROADCAST",
  "payload": {
    "message": "Broadcast enviado"
  }
}
```

### `CLOSE_CONN`
**Request:**
```json
{
  "command": "CLOSE_CONN",
  "payload": null
}
```
**Response:**
```json
{
  "command": "CLOSE_CONN",
  "payload": {
    "message": "Conexión cerrada"
  }
}
```

### `REPORT_USUARIOS`
**Request:**
```json
{
  "command": "REPORT_USUARIOS",
  "payload": null
}
```
**Response:** Lista de `UserSummary` (igual que `LIST_USERS`)

### `REPORT_CANALES`
**Request:**
```json
{
  "command": "REPORT_CANALES",
  "payload": null
}
```
**Response:** Lista de `ChannelSummary` con todos los canales (sin filtro)

### `REPORT_CONECTADOS`
**Request:**
```json
{
  "command": "REPORT_CONECTADOS",
  "payload": null
}
```
**Response:** Lista de `UserSummary` conectados

### `REPORT_AUDIO`
**Descripción:** Obtiene un reporte de todos los mensajes de audio con sus transcripciones.

**Request:**
```json
{
  "command": "REPORT_AUDIO",
  "payload": null
}
```
**Response:** 
```json
{
  "command": "REPORT_AUDIO",
  "payload": [
    {
      "mensajeId": 123,
      "emisorId": 1,
      "receptorId": 2,
      "canalId": null,
      "ruta": "media/audio/usuarios/1/rec_1760597378319.wav",
      "mime": "audio/wav",
      "duracion": 15,
      "transcripcion": "hola cómo estás me gustaría coordinar una reunión"
    }
  ]
}
```

### `REPORT_LOGS`
**Request:**
```json
{
  "command": "REPORT_LOGS",
  "payload": null
}
```
**Response:** Lista de `LogEntryDto`

## Eventos asíncronos

El servidor envía notificaciones mediante:
```json
{
  "command": "EVENT",
  "payload": { ... }
}
```

**Tipos de eventos:**
- `MESSAGE_SYNC`: Sincronización de mensajes (después de LOGIN)
- `NEW_MESSAGE`: Nuevo mensaje privado recibido
- `NEW_CHANNEL_MESSAGE`: Nuevo mensaje en canal
- `LOGIN`: Usuario inicia sesión
- `LOGOUT`: Usuario cierra sesión
- `CHANNEL_CREATED`: Canal creado
- `INVITE_SENT`: Invitación enviada
- Broadcast: String con mensaje del servidor
- `KICKED`: Conexión cerrada por servidor
- `SERVER_SHUTDOWN`: Servidor apagándose

## Errores

```json
{
  "command": "ERROR",
  "payload": {
    "error": "Descripción del problema"
  }
}
```

**Errores comunes:**
- `"Debes iniciar sesión para usar este comando"`
- `"Credenciales inválidas"`
- `"Ya tienes una sesión activa. Solo se permite una sesión por usuario."`
- `"El correo ya está registrado"`
- `"Usuario no encontrado"`
- `"Canal no encontrado"`
- `"Comando desconocido: <nombre>"`
- `"Servidor lleno. Máximo de conexiones alcanzado."` (al intentar conectar)
- `"Datos de audio inválidos: ..."` (audio Base64 corrupto o vacío)
- `"Error guardando audio en el servidor"` (problemas de almacenamiento)

## Eventos Importantes para el Cliente

### MESSAGE_SYNC - Sincronización después de LOGIN
```json
{
  "command": "MESSAGE_SYNC",
  "payload": {
    "mensajes": [
      {
        "id": 123,
        "tipo": "TEXTO",
        "emisor": 1,
        "receptor": 2,
        "timeStamp": "2025-10-16T10:30:00",
        "contenido": "Hola"
      },
      {
        "id": 124,
        "tipo": "AUDIO",
        "emisor": 2,
        "receptor": 1,
        "timeStamp": "2025-10-16T10:31:00",
        "rutaArchivo": "media/audio/usuarios/2/rec_123.wav",
        "transcripcion": "hola cómo estás"
      }
    ],
    "totalMensajes": 2,
    "ultimaSincronizacion": "2025-10-16T11:30:00"
  }
}
```
**Acción del cliente**: Cargar todos los mensajes en la interfaz para mostrar el historial completo.

### Eventos de Mensajes en Tiempo Real

#### NEW_MESSAGE - Nuevo mensaje privado
```json
{
  "command": "NEW_MESSAGE",
  "payload": {
    "id": 125,
    "tipo": "TEXTO",
    "emisor": 2,
    "receptor": 1,
    "timeStamp": "2025-10-16T12:30:00",
    "contenido": "¿Cómo estás?"
  }
}
```

#### NEW_CHANNEL_MESSAGE - Nuevo mensaje en canal
```json
{
  "command": "NEW_CHANNEL_MESSAGE",
  "payload": {
    "id": 126,
    "tipo": "AUDIO",
    "emisor": 3,
    "canalId": 5,
    "timeStamp": "2025-10-16T12:31:00",
    "rutaArchivo": "media/audio/usuarios/3/rec_456.wav",
    "transcripcion": "reunión mañana a las 10"
  }
}
```

**Acción del cliente**: Agregar el mensaje a la interfaz en tiempo real.

## Eventos de Desconexión

El cliente debe detectar estas situaciones:

### 1. EOF - Socket Cerrado
```java
String line = reader.readLine();
if (line == null) {
    // El servidor cerró la conexión
    manejarDesconexion();
}
```

### 2. EVENT con tipo KICKED
El servidor expulsó tu conexión.
```json
{
  "command": "EVENT",
  "payload": {
    "tipo": "KICKED",
    "mensaje": "Tu conexión ha sido cerrada por el servidor",
    "razon": "El administrador cerró tu conexión"
  }
}
```

### 3. EVENT con tipo SERVER_SHUTDOWN
El servidor se está apagando.
```json
{
  "command": "EVENT",
  "payload": {
    "tipo": "SERVER_SHUTDOWN",
    "mensaje": "El servidor se está apagando. Serás desconectado.",
    "razon": "Mantenimiento programado"
  }
}
```

**Acción del cliente**: Cerrar socket, limpiar sesión, volver a login.
