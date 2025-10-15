# Protocolo de comunicación del servidor

Este servidor opera sobre sockets TCP y utiliza **JSON codificado en UTF-8** como formato de intercambio. Cada mensaje corresponde a un objeto JSON serializado en una sola línea, compuesto por las siguientes propiedades:

| Campo    | Tipo    | Descripción |
|----------|---------|-------------|
| `command` | `string` | Identificador del comando o evento. |
| `payload` | `object`/`array`/`string` | Datos específicos del comando. Puede omitirse (`null`) cuando no aplica. |

Los comandos recibidos por el servidor se procesan en `ConnectionHandler`, mientras que las respuestas y notificaciones se generan con la misma estructura a través de `CommandEnvelope`. Además, el `ConnectionRegistry` envía eventos asíncronos a los clientes utilizando el comando `EVENT`.

## Comandos síncronos

A continuación, se describen los comandos soportados, los datos de entrada que esperan y las respuestas que generan. Todos los ejemplos se muestran en JSON.

### `REGISTER`
- **Payload de solicitud** (`RegisterRequest`):
  ```json
  {
    "usuario": "string",
    "email": "string",
    "contrasenia": "string",
    "fotoBase64": "string (opcional)",
    "fotoPath": "string (opcional)",
    "ip": "string"
  }
  ```
- **Respuesta** (`AckResponse`):
  ```json
  {
    "command": "REGISTER",
    "payload": {
      "success": true,
      "message": "Registro exitoso"
    }
  }
  ```
- **Notas**: Al registrarse, la sesión queda autenticada y se publica un evento `LOGIN`.

### `SEND_USER`
- **Payload de solicitud** (`MessageRequest`):
  ```json
  {
    "tipo": "TEXTO | AUDIO | ARCHIVO",
    "contenido": "string (solo para TEXTO)",
    "rutaArchivo": "string (para AUDIO/ARCHIVO)",
    "mime": "string (para AUDIO/ARCHIVO)",
    "duracionSeg": 123 (solo AUDIO),
    "receptor": 42
  }
  ```
- **Respuesta** (`AckResponse`):
  ```json
  {
    "command": "SEND_USER",
    "payload": {
      "success": true,
      "message": "Mensaje enviado"
    }
  }
  ```
- **Notas**: Requiere sesión autenticada. El receptor recibirá un evento `EVENT` con el mensaje persistido.

### `SEND_CHANNEL`
- **Payload de solicitud** (`MessageRequest`):
  ```json
  {
    "tipo": "TEXTO | AUDIO | ARCHIVO",
    "contenido": "string (solo TEXTO)",
    "rutaArchivo": "string (para AUDIO/ARCHIVO)",
    "mime": "string (para AUDIO/ARCHIVO)",
    "duracionSeg": 123 (solo AUDIO),
    "canalId": 99
  }
  ```
- **Respuesta** (`AckResponse`):
  ```json
  {
    "command": "SEND_CHANNEL",
    "payload": {
      "success": true,
      "message": "Mensaje a canal enviado"
    }
  }
  ```
- **Notas**: El servidor agrega automáticamente al remitente al canal y reenvía el mensaje a todos los miembros mediante eventos `EVENT`.

### `CREATE_CHANNEL`
- **Payload de solicitud** (`ChannelRequest`):
  ```json
  {
    "nombre": "string",
    "privado": true
  }
  ```
- **Respuesta** (`Canal`):
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
- **Notas**: Publica un evento `CHANNEL_CREATED` accesible mediante notificaciones `EVENT` para los clientes suscritos.

### `INVITE`
- **Payload de solicitud** (`InviteRequest`):
  ```json
  {
    "canalId": 99,
    "invitadoId": 42
  }
  ```
- **Respuesta** (`AckResponse`):
  ```json
  {
    "command": "INVITE",
    "payload": {
      "success": true,
      "message": "Invitación enviada"
    }
  }
  ```

### `ACCEPT`
- **Payload de solicitud** (`InviteRequest`):
  ```json
  {
    "canalId": 99
  }
  ```
- **Respuesta** (`AckResponse`):
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
- **Payload de solicitud** (`InviteRequest`):
  ```json
  {
    "canalId": 99
  }
  ```
- **Respuesta** (`AckResponse`):
  ```json
  {
    "command": "REJECT",
    "payload": {
      "success": true,
      "message": "Invitación rechazada"
    }
  }
  ```

### `LIST_USERS`
- **Payload de solicitud**: `null`
- **Respuesta** (`List<UserSummary>`):
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
- **Payload de solicitud**: `null`
- **Respuesta** (`List<ChannelSummary>`):
  ```json
  {
    "command": "LIST_CHANNELS",
    "payload": [
      {
        "id": 10,
        "nombre": "general",
        "privado": false,
        "usuarios": [
          {
            "id": 1,
            "usuario": "alice",
            "email": "alice@example.com",
            "conectado": true
          }
        ]
      }
    ]
  }
  ```

### `LIST_CONNECTED`
- **Payload de solicitud**: `null`
- **Respuesta** (`List<UserSummary>`): Igual formato que `LIST_USERS`, filtrado solo por usuarios conectados.

### `BROADCAST`
- **Payload de solicitud**:
  ```json
  {
    "message": "string"
  }
  ```
- **Respuesta** (`AckResponse`):
  ```json
  {
    "command": "BROADCAST",
    "payload": {
      "success": true,
      "message": "Broadcast enviado"
    }
  }
  ```
- **Notas**: Envía el texto indicado a todas las sesiones activas mediante un evento `EVENT` cuyo payload es la cadena original.

### `CLOSE_CONN`
- **Payload de solicitud**: `null`
- **Respuesta** (`AckResponse`):
  ```json
  {
    "command": "CLOSE_CONN",
    "payload": {
      "success": true,
      "message": "Conexión cerrada"
    }
  }
  ```
- **Notas**: Tras la respuesta el servidor cierra la sesión actual.

### Comandos de reporte
Los siguientes comandos comparten la estructura de respuesta `{"command": "<COMANDO>", "payload": [...]}`. Cada uno devuelve una colección especializada:

| Comando | Payload de respuesta |
|---------|---------------------|
| `REPORT_USUARIOS` | Lista de `UserSummary`, equivalente a `LIST_USERS`. |
| `REPORT_CANALES` | Lista de `ChannelSummary`, equivalente a `LIST_CHANNELS`. |
| `REPORT_CONECTADOS` | Lista de `UserSummary` solo con usuarios conectados. |
| `REPORT_AUDIO` | Lista de `AudioMetadataDto` con metadatos de mensajes de audio/archivo. |
| `REPORT_LOGS` | Lista de `LogEntryDto` con los registros de actividad. |

## Notificaciones asíncronas (`EVENT`)

Además de las respuestas directas, el servidor puede enviar notificaciones espontáneas utilizando:

```json
{
  "command": "EVENT",
  "payload": { ... }
}
```

El contenido del payload depende del origen del evento:

- Mensajes enviados a usuarios o canales: instancia de `Mensaje` (`TextoMensaje`, `AudioMensaje` o `ArchivoMensaje`).
- Difusiones (`BROADCAST`): cadena con el mensaje difundido.
- Creación de canales e invitaciones: objetos dominio asociados (`Canal` o mapa con `canalId` / `invitadoId`).

Se recomienda que los clientes distingan el tipo concreto inspeccionando la propiedad `tipo` en los mensajes o el shape del objeto.

## Errores

Cuando un comando no es válido o se produce una excepción, el servidor responde con:

```json
{
  "command": "ERROR",
  "payload": {
    "error": "Descripción del problema"
  }
}
```

Los errores pueden deberse a comandos desconocidos, falta de autenticación, datos inválidos o recursos inexistentes.

