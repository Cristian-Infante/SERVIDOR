# Protocolo de sincronización entre servidores

Los servidores se conectan entre sí mediante **sockets TCP** dedicados al clúster. Cada mensaje se intercambia como una línea en formato **JSON UTF-8** utilizando el siguiente envoltorio común:

```json
{
  "type": "HELLO | SYNC_STATE | CLIENT_CONNECTED | CLIENT_DISCONNECTED | CHANNEL_MEMBERSHIP | DIRECT_MESSAGE | CHANNEL_MESSAGE | SESSION_MESSAGE | BROADCAST",
  "origin": "string",
  "payload": { }
}
```

- `type`: Enum con el tipo de evento P2P.
- `origin`: Identificador del servidor que envía el mensaje.
- `payload`: Contenido específico del evento (ver secciones siguientes).

## Ciclo de vida de la conexión

1. **HELLO**
   - Mensaje inicial al abrir un socket.
   - Permite conocer el `serverId` remoto.
   - Ambas partes envían HELLO y, cuando se reciben, queda establecida la conexión lógica.

   ```json
   {
     "type": "HELLO",
     "origin": "srv-a",
     "payload": {
       "serverId": "srv-a"
     }
   }
   ```

2. **SYNC_STATE**
   - Se envía inmediatamente después del intercambio de `HELLO`.
   - Sincroniza el estado local (sesiones y membresías) con el par.

   ```json
   {
     "type": "SYNC_STATE",
     "origin": "srv-a",
     "payload": {
       "sessions": [
         {
           "serverId": "srv-a",
           "sessionId": "0c6f4e25-5e3a-4d1e-8cb7-1ce5bbf6093f",
           "clienteId": 12,
           "usuario": "carlos",
           "ip": "192.168.1.10",
           "canales": [3, 8]
         }
       ]
     }
   }
   ```

## Eventos de presencia y canales

### CLIENT_CONNECTED

Notifica que un cliente se autenticó en el servidor emisor.

```json
{
  "type": "CLIENT_CONNECTED",
  "origin": "srv-a",
  "payload": {
    "serverId": "srv-a",
    "sessionId": "ce9fb813-3d66-450e-8d20-85f7da7338a0",
    "clienteId": 12,
    "usuario": "carlos",
    "ip": "192.168.1.10",
    "canales": [3, 8]
  }
}
```

### CLIENT_DISCONNECTED

Se emite cuando una sesión remota finaliza (logout o cierre de socket).

```json
{
  "type": "CLIENT_DISCONNECTED",
  "origin": "srv-a",
  "payload": {
    "sessionId": "ce9fb813-3d66-450e-8d20-85f7da7338a0",
    "clienteId": 12
  }
}
```

### CHANNEL_MEMBERSHIP

Propaga cambios de membresía en canales. Actualmente sólo se utiliza la acción `JOIN`.

```json
{
  "type": "CHANNEL_MEMBERSHIP",
  "origin": "srv-a",
  "payload": {
    "sessionId": "ce9fb813-3d66-450e-8d20-85f7da7338a0",
    "clienteId": 12,
    "canalId": 3,
    "action": "JOIN"
  }
}
```

## Reenvío de mensajes

### DIRECT_MESSAGE

Entrega a un usuario alojado en otro servidor un mensaje directo serializado previamente por la lógica principal.

```json
{
  "type": "DIRECT_MESSAGE",
  "origin": "srv-a",
  "payload": {
    "userId": 42,
    "message": {
      "command": "EVENT",
      "payload": {
        "evento": "NEW_MESSAGE",
        "mensajeId": 981,
        "contenido": "hola"
      }
    }
  }
}
```

### CHANNEL_MESSAGE

Reenvía mensajes destinados a miembros de un canal cuyo responsable es otro servidor.

```json
{
  "type": "CHANNEL_MESSAGE",
  "origin": "srv-a",
  "payload": {
    "canalId": 3,
    "message": {
      "command": "EVENT",
      "payload": {
        "evento": "NEW_CHANNEL_MESSAGE",
        "mensajeId": 982,
        "contenido": {
          "rutaArchivo": "media/audio/usuarios/3/rec_456.wav"
        }
      }
    }
  }
}
```

### SESSION_MESSAGE

Permite enviar mensajes puntuales asociados a una sesión (por ejemplo confirmaciones de entrega).

```json
{
  "type": "SESSION_MESSAGE",
  "origin": "srv-a",
  "payload": {
    "sessionId": "ce9fb813-3d66-450e-8d20-85f7da7338a0",
    "message": {
      "command": "EVENT",
      "payload": {
        "tipo": "ACK"
      }
    }
  }
}
```

### BROADCAST

Mensaje genérico que se replica en todos los servidores conectados. El contenido es libre y queda encapsulado bajo `message`.

```json
{
  "type": "BROADCAST",
  "origin": "srv-a",
  "payload": {
    "message": {
      "command": "EVENT",
      "payload": {
        "tipo": "SERVER_ALERT",
        "mensaje": "El servidor srv-a entrará en mantenimiento"
      }
    }
  }
}
```

## Gestión de desconexiones de servidores

Cuando un socket P2P se cierra o falla, se elimina el servidor remoto del registro local y se notifica a los listeners de estado para que la interfaz actualice la lista de nodos disponibles.

