# Métricas sugeridas para Grafana

## Tráfico y salud de TCP (clientes)
- Conexiones activas/por estado: gauge del número de sesiones registradas en ConnectionRegistry y contadores de altas/bajas en ConnectionHandler (inicio en register, fin en cleanup).
- Errores de socket: contador de excepciones (SocketException, IOException) en el loop de escucha para detectar desconexiones abruptas o timeouts.
- Latencia de respuesta por comando: histograma midiendo el tiempo entre recepción (listen) y envío (send) por tipo de comando para ver dónde se producen cuellos de botella.

## Negocio y seguridad (clientes)
- Tasa de autenticaciones: contador de LOGIN exitosos y fallidos (captura de excepciones de validación) + gauge de sesiones autenticadas (clienteId no nulo).
- Comandos por tipo: contador etiquetado por command (REGISTER, SEND_USER, SEND_CHANNEL, INVITE, etc.) para saber qué usan los clientes.
- Errores de negocio: contador de respuestas ERROR y breakdown por mensaje (“Credenciales inválidas”, “Comando no soportado”, etc.).
- Cargas de audio: tamaño promedio y número de UPLOAD_AUDIO para vigilar almacenamiento y ancho de banda.

## Mensajería y eventos (clientes)
- Mensajes entrantes por tipo: contadores de SEND_USER/SEND_CHANNEL y de eventos emitidos (NEW_MESSAGE, NEW_CHANNEL_MESSAGE, INVITE_*, USER_STATUS_CHANGED).
- Backlog/latencia de sincronización: tamaño de totalMensajes en MESSAGE_SYNC tras login para estimar atraso de sincronización.

## Métricas P2P (entre servidores)
- Peers conectados: gauge de connectedPeerIds() y contadores de reconexiones/fallos en connectToPeer/startAcceptor.
- Eventos P2P por tipo: contadores de CLIENT_CONNECTED, CLIENT_DISCONNECTED, CHANNEL_MEMBERSHIP, DIRECT_MESSAGE, CHANNEL_MESSAGE, SESSION_MESSAGE, SYNC_STATE, BROADCAST para observar carga entre nodos.
- Rutas y hops: histograma de longitud de route en mensajes dirigidos para detectar bucles o rutas largas.