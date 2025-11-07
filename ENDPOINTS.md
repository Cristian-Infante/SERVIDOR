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

- Comandos que **NO** requieren autenticación**:** `REGISTER`, `LOGIN`, `PING`, `LIST_USERS`, `LIST_CONNECTED`, `CLOSE_CONN`,
  `REPORT_USUARIOS`, `REPORT_CANALES`, `REPORT_CONECTADOS`, `REPORT_AUDIO`, `REPORT_LOGS`.
- Comandos que **SÍ** validan sesión**:** todos los demás (`UPLOAD_AUDIO`, `SEND_USER`, `SEND_CHANNEL`, `CREATE_CHANNEL`,
  `INVITE`, `ACCEPT`, `REJECT`, `LIST_RECEIVED_INVITATIONS`, `LIST_SENT_INVITATIONS`, `LIST_CHANNELS`, `BROADCAST`, `LOGOUT`).
- `LOGOUT`: Cierra sesión pero mantiene la conexión TCP abierta.
- `CLOSE_CONN`: Cierra sesión y termina la conexión TCP (no requiere estar autenticado).

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
    "success": true,
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
    "success": true,
    "message": "Login exitoso",
    "fotoBase64": "..." // Puede ser null si el usuario no tiene foto
  }
}
```

**Después del login exitoso**, el servidor envía automáticamente:
```json
{
  "command": "MESSAGE_SYNC",
  "payload": {
    "mensajes": [
      /* mensajes enviados y recibidos con metadatos de usuario/canal */
    ],
    "totalMensajes": 25,
    "ultimaSincronizacion": "2025-10-16T11:30:00"
  }
}
```

Los mensajes de audio dentro de `mensajes` incluyen el campo `audioBase64` dentro de `payload.mensajes[*].contenido` con el contenido codificado para que el cliente pueda reproducirlos sin descargar archivos adicionales.

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
    "success": true,
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
    "success": true,
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
- El contenido codificado en Base64 se adjunta durante la sincronización de mensajes (`MESSAGE_SYNC`).

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
    "success": true,
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

**Evento recibido por el destinatario (`command: EVENT`):**
```json
{
  "command": "EVENT",
  "payload": {
    "evento": "NEW_MESSAGE",
    "id": 123,
    "tipoMensaje": "AUDIO",
    "timestamp": "2025-10-16T14:30:00",
    "tipoConversacion": "DIRECTO",
    "emisorId": 1,
    "emisorNombre": "alice",
    "receptorId": 2,
    "receptorNombre": "bob",
    "canalId": null,
    "canalNombre": null,
    "contenido": {
      "rutaArchivo": "media/audio/usuarios/1/rec_1760597378319.wav",
      "mime": "audio/wav",
      "duracionSeg": 15,
      "transcripcion": "hola cómo estás me gustaría coordinar una reunión",
      "audioBase64": "UklGRlIAAABXQVZFZm10IBAAAAABAAEA..."
    }
  }
}
```

> **Importante:** El evento en tiempo real ahora incluye el campo `audioBase64` para que emisor y receptor puedan reproducir el audio sin descargar archivos adicionales.

**Sincronización en tiempo real para el emisor:**

- Después de persistir el mensaje, el servidor emite el mismo evento `NEW_MESSAGE` de vuelta al emisor.
- La entrega al propio emisor se realiza mediante `sendToUser`, que reenvía el payload a **todas** las conexiones activas asociadas a su ID.
- En consecuencia, si el usuario tiene múltiples sesiones abiertas (por ejemplo, web y móvil), todas quedan sincronizadas inmediatamente con el mensaje que acaba de enviar.

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
    "success": true,
    "message": "Mensaje a canal enviado"
  }
}
```

**Evento recibido por los miembros (`command: EVENT`):**
```json
{
  "command": "EVENT",
  "payload": {
    "evento": "NEW_CHANNEL_MESSAGE",
    "id": 456,
    "tipoMensaje": "TEXTO",
    "timestamp": "2025-10-16T14:35:00",
    "tipoConversacion": "CANAL",
    "emisorId": 3,
    "emisorNombre": "carla",
    "receptorId": null,
    "receptorNombre": null,
    "canalId": 99,
    "canalNombre": "desarrollo",
    "canalMiembros": [
      {
        "id": 3,
        "usuario": "carla",
        "email": "carla@example.com",
        "conectado": true
      },
      {
        "id": 8,
        "usuario": "diego",
        "email": "diego@example.com",
        "conectado": false
      }
    ],
    "contenido": {
      "contenido": "Hola a todos"
    }
  }
}
```

**Notas:**
- Mismo flujo que `SEND_USER` para mensajes de audio.
- Todos los miembros del canal reciben el mensaje con la transcripción en tiempo real (cuando aplica).
- Desde esta versión los eventos incluyen `canalMiembros` con los usuarios actuales del canal para facilitar la actualización del cliente.
- El contenido codificado en Base64 para audios se entrega únicamente durante la sincronización (`MESSAGE_SYNC`).

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
    "success": true,
    "message": "Invitación enviada"
  }
}
```

**Notas:**
- Si ya existía una invitación pendiente para el mismo canal y usuario, el servidor la **reactiva** (actualiza `estado` a `PENDIENTE`,
  renueva la fecha y cambia el `invitadorId`) en lugar de devolver un error. Esta reactivación vuelve a disparar las notificaciones
  en tiempo real descritas en la sección de eventos.
- Tanto el invitado como el invitador reciben inmediatamente un `EVENT` con `payload.evento = "INVITE_SENT"` que incluye los metadatos
  e identificador persistido de la invitación.

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
    "success": true,
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
    "success": true,
    "message": "Invitación rechazada"
  }
}
```

### `LIST_RECEIVED_INVITATIONS`
Muestra las invitaciones recibidas (que puedes aceptar o rechazar). El listado refleja las invitaciones replicadas en la base local;
cuando se recibe una invitación desde otro servidor aparecerá aquí tras la sincronización P2P.

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
    "success": true,
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
    "success": true,
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
      "transcripcion": null
    }
  ]
}
```

El campo `transcripcion` puede venir `null` porque la persistencia sólo almacena el texto cuando está disponible.

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

El servidor envía notificaciones mediante mensajes `EVENT`. El contenido varía según el tipo:

```json
{
  "command": "EVENT",
  "payload": {
    "evento": "NEW_MESSAGE" | "NEW_CHANNEL_MESSAGE" | ...,
    /* resto de campos según el tipo */
  }
}
```

**Valores observados de `payload.evento`:**
- `NEW_MESSAGE`: Nuevo mensaje privado recibido (`payload.contenido` depende del tipo de mensaje).
- `NEW_CHANNEL_MESSAGE`: Nuevo mensaje en canal.
- `USER_STATUS_CHANGED`: Actualización del estado de conexión de un usuario.
- `INVITE_SENT`: Invitación recién creada o reactivada.
- `INVITE_ACCEPTED`: Invitación aceptada por el invitado.
- `INVITE_REJECTED`: Invitación rechazada por el invitado.

Otros avisos del servidor utilizan estructuras distintas:
- `KICKED` / `SERVER_SHUTDOWN`: llegan como `ServerNotification` con campos `tipo`, `mensaje` y `razon`.
- Broadcasts simples se entregan como `payload` texto sin envoltorio adicional.

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
- `"Error interno del servidor"` (respuesta genérica cuando se envía un comando protegido sin iniciar sesión).
- `"Credenciales inválidas"`
- `"Ya tienes una sesión activa. Solo se permite una sesión por usuario."`
- `"El email ya está registrado"`
- `"El usuario ya está registrado"`
- `"Usuario inexistente"`
- `"Canal inexistente"`
- `"No existe invitación pendiente para el canal"`
- `"El usuario ya es miembro del canal"`
- `"El contenido del audio no puede estar vacío"`
- `"El ID de usuario es requerido"`
- `"El audio Base64 es inválido"`
- `"No se pudo guardar el archivo de audio"`
- `"La ruta del audio no puede estar vacía"`
- `"Comando no soportado: <nombre>"`

## Eventos Importantes para el Cliente

### MESSAGE_SYNC - Sincronización después de LOGIN
```json
{
  "command": "MESSAGE_SYNC",
  "payload": {
    "mensajes": [
      {
        "id": 123,
        "tipoMensaje": "TEXTO",
        "timestamp": "2025-10-16T10:30:00",
        "tipoConversacion": "DIRECTO",
        "emisorId": 1,
        "emisorNombre": "alice",
        "receptorId": 2,
        "receptorNombre": "bob",
        "canalId": null,
        "canalNombre": null,
        "contenido": {
          "contenido": "Hola"
        }
      },
      {
        "id": 124,
        "tipoMensaje": "AUDIO",
        "timestamp": "2025-10-16T10:31:00",
        "tipoConversacion": "CANAL",
        "emisorId": 2,
        "emisorNombre": "bob",
        "receptorId": null,
        "receptorNombre": null,
        "canalId": 7,
        "canalNombre": "general",
        "contenido": {
          "rutaArchivo": "media/audio/usuarios/2/rec_123.wav",
          "mime": "audio/wav",
          "duracionSeg": 12,
          "transcripcion": "hola cómo estás",
          "audioBase64": "UklGRlIAAABXQVZFZm10IBAAAAABAAEA..."
        }
      }
    ],
    "totalMensajes": 2,
    "ultimaSincronizacion": "2025-10-16T11:30:00"
  }
}
```
**Notas:**
- `mensajes` incluye tanto los mensajes enviados como los recibidos por el usuario.
- `tipoConversacion` puede ser `DIRECTO` (persona a persona) o `CANAL`; sirve para interpretar si se debe usar la metadata de `receptor*` o `canal*`.
- `emisorNombre`, `receptorNombre` y `canalNombre` están resueltos por el servidor para evitar consultas adicionales del cliente.
- La clave `contenido` es un objeto cuya estructura varía según `tipoMensaje`:
  - `TEXTO`: `{ "contenido": "mensaje plano" }`
  - `AUDIO`: `{ "rutaArchivo", "mime", "duracionSeg", "transcripcion", "audioBase64" }`
  - `ARCHIVO`: `{ "rutaArchivo", "mime" }`
**Acción del cliente**: Cargar todos los mensajes en la interfaz para mostrar el historial completo.

### Eventos de Mensajes en Tiempo Real

Los mensajes en tiempo real llegan como `EVENT` y se identifican por `payload.evento`.

#### NEW_MESSAGE - Nuevo mensaje privado
```json
{
  "command": "EVENT",
  "payload": {
    "evento": "NEW_MESSAGE",
    "id": 125,
    "tipoMensaje": "TEXTO",
    "timestamp": "2025-10-16T12:30:00",
    "tipoConversacion": "DIRECTO",
    "emisorId": 2,
    "emisorNombre": "bob",
    "receptorId": 1,
    "receptorNombre": "alice",
    "canalId": null,
    "canalNombre": null,
    "contenido": {
      "contenido": "¿Cómo estás?"
    }
  }
}
```

#### NEW_CHANNEL_MESSAGE - Nuevo mensaje en canal
```json
{
  "command": "EVENT",
  "payload": {
    "evento": "NEW_CHANNEL_MESSAGE",
    "id": 126,
    "tipoMensaje": "AUDIO",
    "timestamp": "2025-10-16T12:31:00",
    "tipoConversacion": "CANAL",
    "emisorId": 3,
    "emisorNombre": "carla",
    "receptorId": null,
    "receptorNombre": null,
    "canalId": 5,
    "canalNombre": "marketing",
    "canalMiembros": [
      {
        "id": 3,
        "usuario": "carla",
        "email": "carla@example.com",
        "conectado": true
      }
    ],
    "contenido": {
      "rutaArchivo": "media/audio/usuarios/3/rec_456.wav",
      "mime": "audio/wav",
      "duracionSeg": 8,
      "transcripcion": "reunión mañana a las 10",
      "audioBase64": "UklGRlIAAABXQVZFZm10IBAAAAABAAEA..."
    }
  }
}
```

**Acción del cliente**: Agregar el mensaje a la interfaz en tiempo real.

#### USER_STATUS_CHANGED - Cambio de estado de conexión de un usuario
```json
{
  "command": "EVENT",
  "payload": {
    "evento": "USER_STATUS_CHANGED",
    "usuarioId": 7,
    "usuarioNombre": "daniela",
    "usuarioEmail": "daniela@example.com",
    "conectado": true,
    "sesionesActivas": 1,
    "timestamp": "2025-10-16T12:35:00.123"
  }
}
```

**Acción del cliente**: Actualizar la lista o indicadores de presencia en tiempo real para reflejar el nuevo estado del usuario.

#### INVITE_SENT - Invitación emitida o reenviada
```json
{
  "command": "EVENT",
  "payload": {
    "evento": "INVITE_SENT",
    "timestamp": "2025-11-06T22:52:41.220091",
    "canalId": 1,
    "canalNombre": "Mis invitaciones",
    "canalPrivado": true,
    "invitadorId": 2,
    "invitadorNombre": "nicole",
    "invitadoId": 5,
    "invitadoNombre": "emmanuel",
    "estado": "PENDIENTE",
    "invitacionId": 17
  }
}
```

**Acción del cliente**: Mostrar la invitación pendiente. Si ya existía, actualizar los metadatos (por ejemplo, el nuevo invitador o la
nueva fecha).

#### INVITE_ACCEPTED / INVITE_REJECTED - Respuesta del invitado
```json
{
  "command": "EVENT",
  "payload": {
    "evento": "INVITE_ACCEPTED",
    "timestamp": "2025-11-06T23:05:12.091422",
    "canalId": 1,
    "canalNombre": "Mis invitaciones",
    "canalPrivado": true,
    "invitadorId": 2,
    "invitadorNombre": "nicole",
    "invitadoId": 5,
    "invitadoNombre": "emmanuel",
    "estado": "ACEPTADA",
    "invitacionId": 17
  }
}
```

Para un rechazo, el evento cambia a `"INVITE_REJECTED"` y `estado` pasa a `"RECHAZADA"`.

**Acción del cliente**: Refrescar la bandeja de invitaciones. El invitador puede retirar la solicitud del listado y el invitado debe
reflejar la membresía actualizada del canal si la invitación fue aceptada.

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

## Integración de chats persona a persona

1. **Descubrir usuarios disponibles**
   - Enviar `LIST_USERS` (no requiere login). El servidor responde con la lista de usuarios y su estado de conexión para que el
     cliente construya el panel de contactos.
2. **Autenticar y sincronizar historial**
   - Hacer `LOGIN` y esperar el `ACK` con `success=true`.
   - Procesar inmediatamente el `MESSAGE_SYNC` para reconstruir el historial local. Los mensajes de audio incluyen `audioBase64`
     dentro de `payload.mensajes[*].contenido.audioBase64`, ideal para precargar el reproductor.
3. **Enviar mensajes**
   - Para texto: mandar `SEND_USER` con `tipo="TEXTO"` y el `contenido` plano.
   - Para audio/archivos: subir primero el recurso con `UPLOAD_AUDIO` (u otro mecanismo equivalente) y reutilizar la
     `rutaArchivo` devuelta al llamar a `SEND_USER`.
4. **Recibir mensajes entrantes**
   - Suscribirse a los mensajes `EVENT` y filtrar `payload.evento === "NEW_MESSAGE"`.
   - Cada evento incluye metadatos (`emisorId`, `receptorId`, `tipoConversacion`) y `payload.contenido` según el tipo de mensaje.
     Los audios traen la transcripción inmediata; el contenido Base64 llega en la próxima sincronización.
5. **Actualizar estados de conexión**
   - Ocasionalmente reenviar `LIST_CONNECTED` para refrescar el listado de usuarios activos o escuchar eventos `LOGIN`/`LOGOUT`
     emitidos por el servidor (vía `EVENT`).
6. **Cerrar sesión o la conexión**
   - `LOGOUT` mantiene el socket abierto, `CLOSE_CONN` lo finaliza. Ambos devuelven `success=true`.
