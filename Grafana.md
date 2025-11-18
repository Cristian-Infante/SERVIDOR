# Métricas sugeridas para Grafana

El servidor expone métricas en formato Prometheus en `http://<host>:<metrics.port>/metrics`.
El puerto se configura en `Aplicacion/Bootstrap/src/main/resources/properties/server.properties` con la clave `metrics.port`
(por defecto `server.port + 100`, por ejemplo 5100 si el servidor escucha en 5000).

La URL del dashboard de Grafana se configura con la propiedad `grafana.url`. El botón "Panel Grafana" de la vista
principal abre esa URL en el navegador por defecto.

## Tráfico y salud de TCP (clientes)
- Conexiones activas/por estado: gauge `chat_tcp_active_connections` más eventos `TCP_CONNECTED`/`TCP_DISCONNECTED`
  observados por `MetricsSessionObserver`.
- Errores de socket: contador `chat_tcp_socket_errors_total` con etiquetas `phase` y `exception` para detectar
  desconexiones abruptas y fallos en el loop de escucha/registro.
- Latencia de respuesta por comando: histograma `chat_command_latency_seconds{command="LOGIN" ...}` y contador
  `chat_commands_total{command="LOGIN",result="ok|error"}` (pensado para instrumentarse en `ConnectionHandler.listen()`).

## Negocio y seguridad (clientes)
- Tasa de autenticaciones: `chat_login_attempts_total{result="success|failure"}` (incrementado en `RegistroServiceImpl`
  y desde los errores de `LOGIN`) + gauge `chat_authenticated_sessions` (sesiones autenticadas activas, derivadas de
  eventos `LOGIN`/`LOGOUT`).
- Comandos por tipo: `chat_commands_total{command="REGISTER"}`, `...="SEND_USER"`, `...="SEND_CHANNEL"`, `...="INVITE"`, etc.
- Errores de negocio: `chat_business_errors_total{reason="credenciales_invalidas",...}` contado desde las respuestas
  `ERROR` (idealmente desde `ConnectionHandler.send("ERROR", ...)`).
- Cargas de audio: `chat_audio_uploads_total` y `chat_audio_upload_size_bytes` (bytes del archivo subido) ya
  instrumentadas en `AudioStorageServiceImpl.guardarAudio(...)`.

## Mensajería y eventos (clientes)
- Mensajes entrantes por tipo: `chat_realtime_events_total{event="NEW_MESSAGE"}`, `...="NEW_CHANNEL_MESSAGE"`,
  `...="INVITE_SENT"`, `...="INVITE_ACCEPTED"`, `...="INVITE_REJECTED"`, junto con `USER_STATUS_CHANGED`
  medido desde `ConexionServiceImpl.notifyStatusChange(...)` usando `ServerMetrics.recordRealtimeEvent(...)`.
- Backlog/latencia de sincronización:
  - `chat_message_sync_backlog_messages`: número de mensajes (`totalMensajes`) en `MESSAGE_SYNC` tras el login.
  - `chat_message_sync_duration_seconds`: tiempo que tarda en construirse la respuesta en `MessageSyncServiceImpl`.

## Métricas P2P (entre servidores)
- Peers conectados: gauge `chat_p2p_connected_peers` pensado para actualizarse desde `ServerPeerManager` cuando se
  conectan/desconectan peers (`updateConnectedPeers(peers.size())`).
- Eventos P2P por tipo: contador `chat_p2p_events_total{type="CLIENT_CONNECTED" | "CLIENT_DISCONNECTED" | "CHANNEL_MESSAGE" | ...}`
  para instrumentar en los métodos `broadcast(...)` y `sendToPeer(...)`.
- Rutas y hops: histograma `chat_p2p_route_hops{type="DIRECT_MESSAGE",...}` observando la longitud de `route` en
  `PeerEnvelope` dentro de `routeEnvelope(...)` para detectar bucles o rutas excesivamente largas.

