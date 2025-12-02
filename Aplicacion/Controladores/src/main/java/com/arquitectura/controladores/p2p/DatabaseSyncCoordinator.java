package com.arquitectura.controladores.p2p;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.arquitectura.entidades.ArchivoMensaje;
import com.arquitectura.entidades.AudioMensaje;
import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.entidades.Mensaje;
import com.arquitectura.entidades.TextoMensaje;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.MensajeRepository;
import com.arquitectura.servicios.eventos.SessionEvent;
import com.arquitectura.servicios.eventos.SessionEventBus;
import com.arquitectura.servicios.eventos.SessionEventType;

/**
 * Coordina la captura y aplicación de estados de base de datos entre servidores pares.
 */
public class DatabaseSyncCoordinator {

    private static final Logger LOGGER = Logger.getLogger(DatabaseSyncCoordinator.class.getName());

    private final ClienteRepository clienteRepository;
    private final CanalRepository canalRepository;
    private final MensajeRepository mensajeRepository;
    private final DataSource dataSource;
    private final SessionEventBus eventBus;

    public DatabaseSyncCoordinator(ClienteRepository clienteRepository,
                                   CanalRepository canalRepository,
                                   MensajeRepository mensajeRepository,
                                   DataSource dataSource,
                                   SessionEventBus eventBus) {
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
        this.mensajeRepository = Objects.requireNonNull(mensajeRepository, "mensajeRepository");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.eventBus = eventBus; // Puede ser null si no se desean notificaciones
    }

    /**
     * Construye un snapshot serializable con la información relevante de la base de datos local.
     */
    public DatabaseSnapshot captureSnapshot() {
        DatabaseSnapshot snapshot = new DatabaseSnapshot();

        List<DatabaseSnapshot.ClienteRecord> clientes = new ArrayList<>();
        for (Cliente cliente : clienteRepository.all()) {
            if (cliente == null || cliente.getId() == null) {
                continue;
            }
            DatabaseSnapshot.ClienteRecord record = new DatabaseSnapshot.ClienteRecord();
            record.setId(cliente.getId());
            record.setUsuario(cliente.getNombreDeUsuario());
            record.setEmail(cliente.getEmail());
            record.setContrasenia(cliente.getContrasenia());
            if (cliente.getFoto() != null && cliente.getFoto().length > 0) {
                record.setFotoBase64(Base64.getEncoder().encodeToString(cliente.getFoto()));
            }
            record.setIp(cliente.getIp());
            record.setEstado(cliente.getEstado());
            clientes.add(record);
        }
        snapshot.setClientes(clientes);

        List<DatabaseSnapshot.CanalRecord> canales = new ArrayList<>();
        List<DatabaseSnapshot.ChannelMembershipRecord> memberships = new ArrayList<>();
        Map<Long, String> canalUuidCache = new HashMap<>();
        for (Canal canal : canalRepository.findAll()) {
            if (canal == null || canal.getId() == null) {
                continue;
            }
            DatabaseSnapshot.CanalRecord record = new DatabaseSnapshot.CanalRecord();
            record.setId(canal.getId());
            record.setUuid(canal.getUuid());
            record.setNombre(canal.getNombre());
            record.setPrivado(canal.getPrivado());
            canales.add(record);
            if (canal.getUuid() != null) {
                canalUuidCache.put(canal.getId(), canal.getUuid());
            }

            List<Cliente> usuarios = canalRepository.findUsers(canal.getId());
            for (Cliente usuario : usuarios) {
                if (usuario == null || usuario.getId() == null) {
                    continue;
                }
                DatabaseSnapshot.ChannelMembershipRecord membership = new DatabaseSnapshot.ChannelMembershipRecord();
                membership.setCanalId(canal.getId());
                membership.setCanalUuid(canal.getUuid());
                membership.setClienteId(usuario.getId());
                memberships.add(membership);
            }
        }
        snapshot.setCanales(canales);
        snapshot.setCanalMiembros(memberships);

        List<DatabaseSnapshot.MensajeRecord> mensajes = new ArrayList<>();
        for (Mensaje mensaje : loadOrderedMessages()) {
            if (mensaje == null || mensaje.getId() == null) {
                continue;
            }
            DatabaseSnapshot.MensajeRecord record = new DatabaseSnapshot.MensajeRecord();
            record.setId(mensaje.getId());
            LocalDateTime ts = mensaje.getTimeStamp();
            record.setTimestamp(ts != null ? ts.toString() : null);
            record.setTipo(mensaje.getTipo());
            record.setEmisorId(mensaje.getEmisor());
            record.setReceptorId(mensaje.getReceptor());
            Long mensajeCanalId = mensaje.getCanalId();
            record.setCanalId(mensajeCanalId);
            if (mensajeCanalId != null) {
                String uuid = canalUuidCache.computeIfAbsent(mensajeCanalId,
                    id -> canalRepository.findById(id).map(Canal::getUuid).orElse(null));
                record.setCanalUuid(uuid);
            }
            if (mensaje instanceof TextoMensaje texto) {
                record.setContenido(texto.getContenido());
            } else if (mensaje instanceof AudioMensaje audio) {
                record.setRutaArchivo(audio.getRutaArchivo());
                record.setMime(audio.getMime());
                record.setDuracionSeg(audio.getDuracionSeg());
                record.setTranscripcion(audio.getTranscripcion());
            } else if (mensaje instanceof ArchivoMensaje archivo) {
                record.setRutaArchivo(archivo.getRutaArchivo());
                record.setMime(archivo.getMime());
            }
            mensajes.add(record);
        }
        snapshot.setMensajes(mensajes);

        snapshot.setInvitaciones(loadInvitaciones());

        return snapshot;
    }

    private List<Mensaje> loadOrderedMessages() {
        List<Mensaje> mensajes = tryRepositoryOrderedFetch();
        if (mensajes != null) {
            return mensajes;
        }
        return queryMessagesFromDatabase();
    }

    private List<Mensaje> tryRepositoryOrderedFetch() {
        try {
            var method = mensajeRepository.getClass().getMethod("findAllOrdered");
            Object result = method.invoke(mensajeRepository);
            if (result instanceof List<?> raw) {
                List<Mensaje> mensajes = new ArrayList<>(raw.size());
                for (Object item : raw) {
                    if (item instanceof Mensaje mensaje) {
                        mensajes.add(mensaje);
                    } else {
                        return null;
                    }
                }
                return mensajes;
            }
        } catch (NoSuchMethodException e) {
            LOGGER.log(Level.FINE, "Repositorio de mensajes no soporta findAllOrdered(), usando consulta directa");
        } catch (ReflectiveOperationException e) {
            LOGGER.log(Level.WARNING, "Error invocando MensajeRepository.findAllOrdered() via reflexión", e);
        }
        return null;
    }

    private List<Mensaje> queryMessagesFromDatabase() {
        String sql = "SELECT id, timestamp, tipo, emisor_id, receptor_id, canal_id, contenido, ruta_archivo, mime, duracion_seg, transcripcion " +
            "FROM mensajes ORDER BY id";
        List<Mensaje> mensajes = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                mensajes.add(mapMensaje(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error consultando mensajes para snapshot", e);
        }
        return mensajes;
    }

    private Mensaje mapMensaje(ResultSet rs) throws SQLException {
        String tipo = rs.getString("tipo");
        Mensaje mensaje;
        switch (tipo) {
            case "AUDIO" -> {
                AudioMensaje audio = new AudioMensaje();
                audio.setRutaArchivo(rs.getString("ruta_archivo"));
                audio.setMime(rs.getString("mime"));
                audio.setDuracionSeg(rs.getInt("duracion_seg"));
                audio.setTranscripcion(rs.getString("transcripcion"));
                mensaje = audio;
            }
            case "ARCHIVO" -> {
                ArchivoMensaje archivo = new ArchivoMensaje();
                archivo.setRutaArchivo(rs.getString("ruta_archivo"));
                archivo.setMime(rs.getString("mime"));
                mensaje = archivo;
            }
            default -> {
                TextoMensaje texto = new TextoMensaje();
                texto.setContenido(rs.getString("contenido"));
                mensaje = texto;
            }
        }
        mensaje.setId(rs.getLong("id"));
        Timestamp ts = rs.getTimestamp("timestamp");
        mensaje.setTimeStamp(ts != null ? ts.toLocalDateTime() : null);
        mensaje.setTipo(tipo);
        mensaje.setEmisor(rs.getLong("emisor_id"));
        mensaje.setReceptor(getNullableLong(rs, "receptor_id"));
        mensaje.setCanalId(getNullableLong(rs, "canal_id"));
        return mensaje;
    }

    private List<DatabaseSnapshot.InvitationRecord> loadInvitaciones() {
        String sql = "SELECT i.id, i.canal_id, c.uuid AS canal_uuid, i.invitador_id, i.invitado_id, i.fecha_invitacion, i.estado " +
            "FROM invitaciones i LEFT JOIN canales c ON c.id = i.canal_id ORDER BY i.id";
        List<DatabaseSnapshot.InvitationRecord> invitaciones = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                DatabaseSnapshot.InvitationRecord record = new DatabaseSnapshot.InvitationRecord();
                record.setId(rs.getLong("id"));
                record.setCanalId(rs.getLong("canal_id"));
                record.setCanalUuid(rs.getString("canal_uuid"));
                record.setInvitadorId(rs.getLong("invitador_id"));
                record.setInvitadoId(rs.getLong("invitado_id"));
                Timestamp ts = rs.getTimestamp("fecha_invitacion");
                record.setFechaInvitacion(ts != null ? ts.toLocalDateTime().toString() : null);
                record.setEstado(rs.getString("estado"));
                invitaciones.add(record);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error consultando invitaciones para snapshot", e);
        }
        return invitaciones;
    }

    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * Aplica el snapshot recibido sobrescribiendo/insertando registros de la base local.
     * @return {@code true} si se realizaron cambios en la base.
     */
    public boolean applySnapshot(DatabaseSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return false;
        }

        Connection connection = null;
        boolean changed = false;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);

            ClientSyncResult clientResult = syncClientes(connection, snapshot.getClientes());
            ChannelSyncResult channelResult = syncCanales(connection, snapshot.getCanales());
            changed |= clientResult.changed();
            changed |= channelResult.changed();
            changed |= syncMemberships(connection, snapshot.getCanalMiembros(), clientResult.idMapping(), channelResult.idMapping());
            changed |= syncMensajes(connection, snapshot.getMensajes(), clientResult.idMapping(), channelResult.idMapping());
            changed |= syncInvitaciones(connection, snapshot.getInvitaciones(), clientResult.idMapping(), channelResult.idMapping());

            connection.commit();
            return changed;
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    LOGGER.log(Level.WARNING, "Error revirtiendo sincronización de base de datos", rollbackError);
                }
            }
            throw new IllegalStateException("Error aplicando snapshot de base de datos", e);
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(true);
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    private ClientSyncResult syncClientes(Connection connection, List<DatabaseSnapshot.ClienteRecord> clientes) throws SQLException {
        if (clientes == null || clientes.isEmpty()) {
            return ClientSyncResult.empty();
        }
        boolean changed = false;
        java.util.Map<Long, Long> idMapping = new java.util.HashMap<>();
        String upsertSql = "INSERT INTO clientes(id, usuario, email, contrasenia, foto, ip, estado) " +
            "VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
            "usuario=VALUES(usuario), email=VALUES(email), contrasenia=VALUES(contrasenia), " +
            "foto=VALUES(foto), ip=VALUES(ip), estado=VALUES(estado)";
        String insertSql = "INSERT INTO clientes(usuario, email, contrasenia, foto, ip, estado) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement upsert = connection.prepareStatement(upsertSql);
             PreparedStatement insert = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            for (DatabaseSnapshot.ClienteRecord record : clientes) {
                if (record.getId() == null) {
                    continue;
                }
                Long resolvedId = resolveExistingClientId(connection, record);
                if (resolvedId != null) {
                    changed |= executeClienteUpsert(upsert, resolvedId, record);
                    idMapping.put(record.getId(), resolvedId);
                    continue;
                }

                if (hasConflictingIdentity(connection, record)) {
                    long generatedId = insertClienteWithoutId(connection, insert, record);
                    idMapping.put(record.getId(), generatedId);
                    changed = true;
                    continue;
                }

                long targetId = record.getId();
                changed |= executeClienteUpsert(upsert, targetId, record);
                idMapping.put(record.getId(), targetId);
            }
        }
        return new ClientSyncResult(changed, idMapping);
    }

    private boolean hasConflictingIdentity(Connection connection, DatabaseSnapshot.ClienteRecord record) throws SQLException {
        if (record.getId() == null) {
            return false;
        }
        String sql = "SELECT usuario, email FROM clientes WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, record.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String currentUsuario = rs.getString("usuario");
                    String currentEmail = rs.getString("email");
                    boolean sameUsuario = Objects.equals(currentUsuario, record.getUsuario());
                    boolean sameEmail = Objects.equals(currentEmail, record.getEmail());
                    return !(sameUsuario || sameEmail);
                }
            }
        }
        return false;
    }

    private boolean executeClienteUpsert(PreparedStatement ps, long targetId, DatabaseSnapshot.ClienteRecord record) throws SQLException {
        try {
            bindClienteUpsert(ps, targetId, record, true);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            // Verificar si es error de paquete muy grande (código 1153)
            if (ex.getErrorCode() == 1153) {
                logPacketTooBig(record, ex);
                bindClienteUpsert(ps, targetId, record, false);
                return ps.executeUpdate() > 0;
            }
            throw ex;
        }
    }

    private long insertClienteWithoutId(Connection connection,
                                        PreparedStatement ps,
                                        DatabaseSnapshot.ClienteRecord record) throws SQLException {
        try {
            bindClienteInsert(ps, record, true);
            ps.executeUpdate();
        } catch (SQLException ex) {
            // Verificar si es error de paquete muy grande (código 1153)
            if (ex.getErrorCode() == 1153) {
                logPacketTooBig(record, ex);
                bindClienteInsert(ps, record, false);
                ps.executeUpdate();
            } else {
                throw ex;
            }
        }
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getLong(1);
            }
        }
        Long fallback = resolveExistingClientId(connection, record);
        if (fallback != null) {
            return fallback;
        }
        throw new SQLException("No se pudo obtener el ID generado para el cliente " + record.getUsuario());
    }

    private void bindClienteUpsert(PreparedStatement ps,
                                   long targetId,
                                   DatabaseSnapshot.ClienteRecord record,
                                   boolean includePhoto) throws SQLException {
        ps.setLong(1, targetId);
        ps.setString(2, record.getUsuario());
        ps.setString(3, record.getEmail());
        ps.setString(4, record.getContrasenia());
        if (includePhoto && record.getFotoBase64() != null && !record.getFotoBase64().isBlank()) {
            ps.setBytes(5, Base64.getDecoder().decode(record.getFotoBase64()));
        } else {
            ps.setNull(5, Types.BLOB);
        }
        if (record.getIp() != null && !record.getIp().isBlank()) {
            ps.setString(6, record.getIp());
        } else {
            ps.setNull(6, Types.VARCHAR);
        }
        if (record.getEstado() != null) {
            ps.setBoolean(7, record.getEstado());
        } else {
            ps.setNull(7, Types.TINYINT);
        }
    }

    private void bindClienteInsert(PreparedStatement ps,
                                   DatabaseSnapshot.ClienteRecord record,
                                   boolean includePhoto) throws SQLException {
        ps.setString(1, record.getUsuario());
        ps.setString(2, record.getEmail());
        ps.setString(3, record.getContrasenia());
        if (includePhoto && record.getFotoBase64() != null && !record.getFotoBase64().isBlank()) {
            ps.setBytes(4, Base64.getDecoder().decode(record.getFotoBase64()));
        } else {
            ps.setNull(4, Types.BLOB);
        }
        if (record.getIp() != null && !record.getIp().isBlank()) {
            ps.setString(5, record.getIp());
        } else {
            ps.setNull(5, Types.VARCHAR);
        }
        if (record.getEstado() != null) {
            ps.setBoolean(6, record.getEstado());
        } else {
            ps.setNull(6, Types.TINYINT);
        }
    }

    private void logPacketTooBig(DatabaseSnapshot.ClienteRecord record, SQLException ex) {
        String identificador = record.getUsuario();
        if (identificador == null || identificador.isBlank()) {
            identificador = record.getEmail() != null ? record.getEmail() : String.valueOf(record.getId());
        }
        LOGGER.log(Level.WARNING,
            "Foto de perfil de " + identificador +
                " excede el tamaño permitido por el servidor. Se omitirá durante la sincronización.",
            ex);
    }

    private void logPacketTooBig(DatabaseSnapshot.MensajeRecord mensaje, SQLException ex) {
        LOGGER.log(Level.WARNING,
            "Mensaje con ID " + mensaje.getId() + 
                " excede el tamaño permitido por el servidor. Se omitirá durante la sincronización.",
            ex);
    }

    private Long resolveExistingClientId(Connection connection, DatabaseSnapshot.ClienteRecord record) throws SQLException {
        Long id = null;
        if (record.getUsuario() != null && !record.getUsuario().isBlank()) {
            id = querySingleLong(connection, "SELECT id FROM clientes WHERE usuario=?", record.getUsuario());
        }
        if (id == null && record.getEmail() != null && !record.getEmail().isBlank()) {
            id = querySingleLong(connection, "SELECT id FROM clientes WHERE email=?", record.getEmail());
        }
        return id;
    }

    private Long querySingleLong(Connection connection, String sql, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    private Long querySingleLong(Connection connection, String sql, Long value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    private ChannelSyncResult syncCanales(Connection connection, List<DatabaseSnapshot.CanalRecord> canales) throws SQLException {
        if (canales == null || canales.isEmpty()) {
            return ChannelSyncResult.empty();
        }
        boolean changed = false;
        java.util.Map<Long, Long> idMapping = new java.util.HashMap<>();
        for (DatabaseSnapshot.CanalRecord record : canales) {
            if (record == null) {
                continue;
            }
            normalizeChannelRecord(record);
            Long localId = findChannelId(connection, record);
            if (localId == null) {
                localId = insertChannel(connection, record);
                if (localId != null) {
                    changed = true;
                }
            } else {
                changed |= updateChannel(connection, localId, record);
            }
            if (record.getId() != null && localId != null) {
                idMapping.put(record.getId(), localId);
            }
        }
        return new ChannelSyncResult(changed, idMapping);
    }

    private boolean syncMemberships(Connection connection,
                                    List<DatabaseSnapshot.ChannelMembershipRecord> memberships,
                                    java.util.Map<Long, Long> clientIdMap,
                                    java.util.Map<Long, Long> channelIdMap) throws SQLException {
        if (memberships == null || memberships.isEmpty()) {
            return false;
        }
        boolean changed = false;
        String sql = "INSERT INTO canal_clientes(canal_id, cliente_id) VALUES(?,?) " +
            "ON DUPLICATE KEY UPDATE cliente_id=VALUES(cliente_id)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (DatabaseSnapshot.ChannelMembershipRecord record : memberships) {
                if (record.getClienteId() == null) {
                    continue;
                }
                Long localCanalId = resolveChannelId(connection, record.getCanalId(), record.getCanalUuid(), channelIdMap);
                if (localCanalId == null) {
                    continue;
                }
                ps.setLong(1, localCanalId);
                ps.setLong(2, resolveClientId(record.getClienteId(), clientIdMap));
                changed |= ps.executeUpdate() > 0;
            }
        }
        return changed;
    }

    private boolean syncMensajes(Connection connection,
                                 List<DatabaseSnapshot.MensajeRecord> mensajes,
                                 java.util.Map<Long, Long> clientIdMap,
                                 java.util.Map<Long, Long> channelIdMap) throws SQLException {
        if (mensajes == null || mensajes.isEmpty()) {
            return false;
        }
        boolean changed = false;
        String sql = "INSERT INTO mensajes(id, timestamp, tipo, emisor_id, receptor_id, canal_id, contenido, ruta_archivo, mime, " +
            "duracion_seg, transcripcion) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
            "timestamp=VALUES(timestamp), tipo=VALUES(tipo), emisor_id=VALUES(emisor_id), receptor_id=VALUES(receptor_id), " +
            "canal_id=VALUES(canal_id), contenido=VALUES(contenido), ruta_archivo=VALUES(ruta_archivo), mime=VALUES(mime), " +
            "duracion_seg=VALUES(duracion_seg), transcripcion=VALUES(transcripcion)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (DatabaseSnapshot.MensajeRecord record : mensajes) {
                if (record.getId() == null) {
                    continue;
                }
                ps.setLong(1, record.getId());
                if (record.getTimestamp() != null && !record.getTimestamp().isBlank()) {
                    ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.parse(record.getTimestamp())));
                } else {
                    ps.setNull(2, Types.TIMESTAMP);
                }
                ps.setString(3, record.getTipo());
                if (record.getEmisorId() != null) {
                    ps.setLong(4, resolveClientId(record.getEmisorId(), clientIdMap));
                } else {
                    ps.setNull(4, Types.BIGINT);
                }
                if (record.getReceptorId() != null) {
                    ps.setLong(5, resolveClientId(record.getReceptorId(), clientIdMap));
                } else {
                    ps.setNull(5, Types.BIGINT);
                }
                Long localCanalId = resolveChannelId(connection, record.getCanalId(), record.getCanalUuid(), channelIdMap);
                if (localCanalId != null) {
                    ps.setLong(6, localCanalId);
                } else {
                    ps.setNull(6, Types.BIGINT);
                }
                ps.setString(7, record.getContenido());
                ps.setString(8, record.getRutaArchivo());
                ps.setString(9, record.getMime());
                if (record.getDuracionSeg() != null) {
                    ps.setInt(10, record.getDuracionSeg());
                } else {
                    ps.setNull(10, Types.INTEGER);
                }
                ps.setString(11, record.getTranscripcion());
                changed |= ps.executeUpdate() > 0;
            }
        }
        return changed;
    }

    private boolean syncInvitaciones(Connection connection,
                                     List<DatabaseSnapshot.InvitationRecord> invitaciones,
                                     java.util.Map<Long, Long> clientIdMap,
                                     java.util.Map<Long, Long> channelIdMap) throws SQLException {
        if (invitaciones == null || invitaciones.isEmpty()) {
            return false;
        }
        boolean changed = false;
        String sqlWithId = "INSERT INTO invitaciones(id, canal_id, invitador_id, invitado_id, fecha_invitacion, estado) " +
            "VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE canal_id=VALUES(canal_id), invitador_id=VALUES(invitador_id), " +
            "invitado_id=VALUES(invitado_id), fecha_invitacion=VALUES(fecha_invitacion), estado=VALUES(estado)";
        String sqlWithoutId = "INSERT INTO invitaciones(canal_id, invitador_id, invitado_id, fecha_invitacion, estado) " +
            "VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE invitador_id=VALUES(invitador_id), fecha_invitacion=VALUES(fecha_invitacion), " +
            "estado=VALUES(estado)";
        
        // Lista para almacenar invitaciones sincronizadas exitosamente
        List<InvitationNotificationData> notificationsToSend = new ArrayList<>();
        
        try (PreparedStatement withId = connection.prepareStatement(sqlWithId);
             PreparedStatement withoutId = connection.prepareStatement(sqlWithoutId)) {
            for (DatabaseSnapshot.InvitationRecord record : invitaciones) {
                if (record.getInvitadoId() == null && record.getInvitadoEmail() == null) {
                    continue;
                }
                Long localCanalId = resolveChannelId(connection, record.getCanalId(), record.getCanalUuid(), channelIdMap);
                if (localCanalId == null) {
                    continue;
                }
                Timestamp timestamp = null;
                if (record.getFechaInvitacion() != null && !record.getFechaInvitacion().isBlank()) {
                    timestamp = Timestamp.valueOf(LocalDateTime.parse(record.getFechaInvitacion()));
                }
                
                // Resolver IDs usando emails cuando estén disponibles (más confiable entre servidores)
                Long invitadorId = resolveClientIdWithEmail(record.getInvitadorId(), record.getInvitadorEmail(), clientIdMap);
                Long invitadoId = resolveClientIdWithEmail(record.getInvitadoId(), record.getInvitadoEmail(), clientIdMap);
                
                if (invitadoId == null) {
                    LOGGER.fine(() -> "No se pudo resolver invitadoId para invitación, email: " + record.getInvitadoEmail());
                    continue;
                }

                boolean updated = false;
                if (record.getId() != null) {
                    withId.setLong(1, record.getId());
                    withId.setLong(2, localCanalId);
                    if (invitadorId != null) {
                        withId.setLong(3, invitadorId);
                    } else {
                        withId.setNull(3, Types.BIGINT);
                    }
                    withId.setLong(4, invitadoId);
                    if (timestamp != null) {
                        withId.setTimestamp(5, timestamp);
                    } else {
                        withId.setNull(5, Types.TIMESTAMP);
                    }
                    if (record.getEstado() != null) {
                        withId.setString(6, record.getEstado());
                    } else {
                        withId.setNull(6, Types.VARCHAR);
                    }
                    updated = withId.executeUpdate() > 0;
                } else {
                    withoutId.setLong(1, localCanalId);
                    if (invitadorId != null) {
                        withoutId.setLong(2, invitadorId);
                    } else {
                        withoutId.setNull(2, Types.BIGINT);
                    }
                    withoutId.setLong(3, invitadoId);
                    if (timestamp != null) {
                        withoutId.setTimestamp(4, timestamp);
                    } else {
                        withoutId.setNull(4, Types.TIMESTAMP);
                    }
                    if (record.getEstado() != null) {
                        withoutId.setString(5, record.getEstado());
                    } else {
                        withoutId.setNull(5, Types.VARCHAR);
                    }
                    updated = withoutId.executeUpdate() > 0;
                }
                
                if (updated) {
                    changed = true;
                    // Agregar a la lista de notificaciones solo para invitaciones pendientes
                    if ("PENDIENTE".equals(record.getEstado())) {
                        notificationsToSend.add(new InvitationNotificationData(
                            localCanalId, record.getCanalUuid(), invitadorId, invitadoId
                        ));
                    }
                }
            }
        }
        
        // Publicar eventos de invitación después de que el commit haya sido exitoso
        // (esto se ejecuta antes del commit, pero los eventos se publican al eventBus)
        if (eventBus != null && !notificationsToSend.isEmpty()) {
            for (InvitationNotificationData notification : notificationsToSend) {
                publishInvitationEvent(notification);
            }
        }
        
        return changed;
    }
    
    /**
     * Publica un evento de invitación al eventBus para notificar a los usuarios locales.
     */
    private void publishInvitationEvent(InvitationNotificationData data) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("canalId", data.canalId);
            payload.put("canalUuid", data.canalUuid);
            payload.put("invitadorId", data.invitadorId);
            payload.put("invitadoId", data.invitadoId);
            
            SessionEvent event = new SessionEvent(
                SessionEventType.INVITE_SENT,
                null, // sessionId - no aplica para sync
                data.invitadorId,
                payload
            );
            eventBus.publish(event);
            LOGGER.fine(() -> "Publicado evento INVITE_SENT para usuario " + data.invitadoId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error publicando evento de invitación sincronizada", e);
        }
    }
    
    /**
     * Datos necesarios para notificar una invitación sincronizada.
     */
    private static record InvitationNotificationData(
        Long canalId,
        String canalUuid,
        Long invitadorId,
        Long invitadoId
    ) {}
    
    /**
     * Resuelve el ID de cliente local usando primero el email (si está disponible) 
     * y luego el mapeo de IDs como fallback.
     */
    private Long resolveClientIdWithEmail(Long originalId, String email, java.util.Map<Long, Long> clientIdMap) {
        // Primero intentar por email (más confiable entre servidores)
        if (email != null && !email.isBlank()) {
            Cliente cliente = clienteRepository.findByEmail(email).orElse(null);
            if (cliente != null) {
                return cliente.getId();
            }
        }
        // Fallback al mapeo de IDs
        if (originalId != null) {
            return resolveClientId(originalId, clientIdMap);
        }
        return null;
    }

    private void normalizeChannelRecord(DatabaseSnapshot.CanalRecord record) {
        if (record.getUuid() == null || record.getUuid().isBlank()) {
            record.setUuid(UUID.randomUUID().toString());
        }
    }

    private Long findChannelId(Connection connection, DatabaseSnapshot.CanalRecord record) throws SQLException {
        String uuid = normalizeUuid(record.getUuid());
        Long recordId = record.getId();
        if (uuid != null) {
            Long byUuid = querySingleLong(connection, "SELECT id FROM canales WHERE uuid=?", uuid);
            if (byUuid != null) {
                return byUuid;
            }
            if (recordId != null) {
                ChannelRowState row = fetchChannelRowById(connection, recordId);
                if (!row.exists()) {
                    return null;
                }
                if (row.uuid() == null || uuid.equals(row.uuid())) {
                    return recordId;
                }
                return null;
            }
            return null;
        }
        if (recordId != null) {
            return querySingleLong(connection, "SELECT id FROM canales WHERE id=?", recordId);
        }
        return null;
    }

    private Long insertChannel(Connection connection, DatabaseSnapshot.CanalRecord record) throws SQLException {
        if (record.getId() != null) {
            String sqlWithId = "INSERT INTO canales(id, uuid, nombre, privado) VALUES(?,?,?,?)";
            try (PreparedStatement ps = connection.prepareStatement(sqlWithId)) {
                ps.setLong(1, record.getId());
                ps.setString(2, record.getUuid());
                ps.setString(3, record.getNombre());
                setNullableBoolean(ps, 4, record.getPrivado());
                ps.executeUpdate();
                return record.getId();
            } catch (SQLIntegrityConstraintViolationException ignored) {
                ChannelRowState row = fetchChannelRowById(connection, record.getId());
                if (row.exists() && (row.uuid() == null || row.uuid().equals(record.getUuid()))) {
                    updateChannel(connection, record.getId(), record);
                    return record.getId();
                }
            }
        }
        String sql = "INSERT INTO canales(uuid, nombre, privado) VALUES(?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, record.getUuid());
            ps.setString(2, record.getNombre());
            setNullableBoolean(ps, 3, record.getPrivado());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLIntegrityConstraintViolationException ignored) {
            Long existing = querySingleLong(connection, "SELECT id FROM canales WHERE uuid=?", record.getUuid());
            if (existing != null) {
                updateChannel(connection, existing, record);
                return existing;
            }
        }
        return querySingleLong(connection, "SELECT id FROM canales WHERE uuid=?", record.getUuid());
    }

    private boolean updateChannel(Connection connection, Long localId, DatabaseSnapshot.CanalRecord record) throws SQLException {
        String sql = "UPDATE canales SET nombre=?, privado=?, uuid=? WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, record.getNombre());
            setNullableBoolean(ps, 2, record.getPrivado());
            ps.setString(3, record.getUuid());
            ps.setLong(4, localId);
            return ps.executeUpdate() > 0;
        }
    }

    private void setNullableBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value != null) {
            ps.setBoolean(index, value);
        } else {
            ps.setNull(index, Types.TINYINT);
        }
    }

    private Long resolveChannelId(Connection connection,
                                  Long originalId,
                                  String canalUuid,
                                  java.util.Map<Long, Long> channelIdMap) throws SQLException {
        if (originalId != null && channelIdMap != null && channelIdMap.containsKey(originalId)) {
            return channelIdMap.get(originalId);
        }
        if (canalUuid != null && !canalUuid.isBlank()) {
            Long byUuid = querySingleLong(connection, "SELECT id FROM canales WHERE uuid=?", canalUuid);
            if (byUuid != null) {
                if (channelIdMap != null && originalId != null) {
                    channelIdMap.put(originalId, byUuid);
                }
                return byUuid;
            }
            if (originalId != null) {
                ChannelRowState row = fetchChannelRowById(connection, originalId);
                if (row.exists() && (row.uuid() == null || canalUuid.equals(row.uuid()))) {
                    if (channelIdMap != null) {
                        channelIdMap.put(originalId, originalId);
                    }
                    return originalId;
                }
            }
            return null;
        }
        if (originalId != null) {
            Long byId = querySingleLong(connection, "SELECT id FROM canales WHERE id=?", originalId);
            if (byId != null && channelIdMap != null) {
                channelIdMap.put(originalId, byId);
            }
            return byId;
        }
        return null;
    }

    private long resolveClientId(Long originalId, java.util.Map<Long, Long> clientIdMap) {
        return clientIdMap != null && clientIdMap.containsKey(originalId)
            ? clientIdMap.get(originalId)
            : originalId;
    }

    private String normalizeUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        String trimmed = uuid.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ChannelRowState fetchChannelRowById(Connection connection, Long id) throws SQLException {
        if (id == null) {
            return new ChannelRowState(false, null);
        }
        String sql = "SELECT uuid FROM canales WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ChannelRowState(true, normalizeUuid(rs.getString(1)));
                }
            }
        }
        return new ChannelRowState(false, null);
    }

    private record ClientSyncResult(boolean changed, java.util.Map<Long, Long> idMapping) {
        static ClientSyncResult empty() {
            return new ClientSyncResult(false, java.util.Collections.emptyMap());
        }
    }

    private record ChannelSyncResult(boolean changed, java.util.Map<Long, Long> idMapping) {
        static ChannelSyncResult empty() {
            return new ChannelSyncResult(false, new java.util.HashMap<>());
        }
    }

    private record ChannelRowState(boolean exists, String uuid) {
    }
}

