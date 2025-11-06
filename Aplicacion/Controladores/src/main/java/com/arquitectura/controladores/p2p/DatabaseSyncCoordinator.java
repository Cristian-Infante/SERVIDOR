package com.arquitectura.controladores.p2p;

import com.arquitectura.entidades.ArchivoMensaje;
import com.arquitectura.entidades.AudioMensaje;
import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.entidades.Mensaje;
import com.arquitectura.entidades.TextoMensaje;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.MensajeRepository;
import com.mysql.cj.jdbc.exceptions.PacketTooBigException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordina la captura y aplicación de estados de base de datos entre servidores pares.
 */
public class DatabaseSyncCoordinator {

    private static final Logger LOGGER = Logger.getLogger(DatabaseSyncCoordinator.class.getName());

    private final ClienteRepository clienteRepository;
    private final CanalRepository canalRepository;
    private final MensajeRepository mensajeRepository;
    private final DataSource dataSource;

    public DatabaseSyncCoordinator(ClienteRepository clienteRepository,
                                   CanalRepository canalRepository,
                                   MensajeRepository mensajeRepository,
                                   DataSource dataSource) {
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
        this.mensajeRepository = Objects.requireNonNull(mensajeRepository, "mensajeRepository");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
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
        for (Canal canal : canalRepository.findAll()) {
            if (canal == null || canal.getId() == null) {
                continue;
            }
            DatabaseSnapshot.CanalRecord record = new DatabaseSnapshot.CanalRecord();
            record.setId(canal.getId());
            record.setNombre(canal.getNombre());
            record.setPrivado(canal.getPrivado());
            canales.add(record);

            List<Cliente> usuarios = canalRepository.findUsers(canal.getId());
            for (Cliente usuario : usuarios) {
                if (usuario == null || usuario.getId() == null) {
                    continue;
                }
                DatabaseSnapshot.ChannelMembershipRecord membership = new DatabaseSnapshot.ChannelMembershipRecord();
                membership.setCanalId(canal.getId());
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
            record.setCanalId(mensaje.getCanalId());
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
            changed |= clientResult.changed();
            changed |= syncCanales(connection, snapshot.getCanales());
            changed |= syncMemberships(connection, snapshot.getCanalMiembros(), clientResult.idMapping());
            changed |= syncMensajes(connection, snapshot.getMensajes(), clientResult.idMapping());

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
        } catch (PacketTooBigException ex) {
            logPacketTooBig(record, ex);
            bindClienteUpsert(ps, targetId, record, false);
            return ps.executeUpdate() > 0;
        }
    }

    private long insertClienteWithoutId(Connection connection,
                                        PreparedStatement ps,
                                        DatabaseSnapshot.ClienteRecord record) throws SQLException {
        try {
            bindClienteInsert(ps, record, true);
            ps.executeUpdate();
        } catch (PacketTooBigException ex) {
            logPacketTooBig(record, ex);
            bindClienteInsert(ps, record, false);
            ps.executeUpdate();
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

    private void logPacketTooBig(DatabaseSnapshot.ClienteRecord record, PacketTooBigException ex) {
        String identificador = record.getUsuario();
        if (identificador == null || identificador.isBlank()) {
            identificador = record.getEmail() != null ? record.getEmail() : String.valueOf(record.getId());
        }
        LOGGER.log(Level.WARNING,
            "Foto de perfil de " + identificador +
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

    private boolean syncCanales(Connection connection, List<DatabaseSnapshot.CanalRecord> canales) throws SQLException {
        if (canales == null || canales.isEmpty()) {
            return false;
        }
        boolean changed = false;
        String sql = "INSERT INTO canales(id, nombre, privado) VALUES(?,?,?) " +
            "ON DUPLICATE KEY UPDATE nombre=VALUES(nombre), privado=VALUES(privado)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (DatabaseSnapshot.CanalRecord record : canales) {
                if (record.getId() == null) {
                    continue;
                }
                ps.setLong(1, record.getId());
                ps.setString(2, record.getNombre());
                if (record.getPrivado() != null) {
                    ps.setBoolean(3, record.getPrivado());
                } else {
                    ps.setNull(3, Types.TINYINT);
                }
                changed |= ps.executeUpdate() > 0;
            }
        }
        return changed;
    }

    private boolean syncMemberships(Connection connection,
                                    List<DatabaseSnapshot.ChannelMembershipRecord> memberships,
                                    java.util.Map<Long, Long> clientIdMap) throws SQLException {
        if (memberships == null || memberships.isEmpty()) {
            return false;
        }
        boolean changed = false;
        String sql = "INSERT INTO canal_clientes(canal_id, cliente_id) VALUES(?,?) " +
            "ON DUPLICATE KEY UPDATE cliente_id=VALUES(cliente_id)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (DatabaseSnapshot.ChannelMembershipRecord record : memberships) {
                if (record.getCanalId() == null || record.getClienteId() == null) {
                    continue;
                }
                ps.setLong(1, record.getCanalId());
                ps.setLong(2, resolveClientId(record.getClienteId(), clientIdMap));
                changed |= ps.executeUpdate() > 0;
            }
        }
        return changed;
    }

    private boolean syncMensajes(Connection connection,
                                 List<DatabaseSnapshot.MensajeRecord> mensajes,
                                 java.util.Map<Long, Long> clientIdMap) throws SQLException {
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
                if (record.getCanalId() != null) {
                    ps.setLong(6, record.getCanalId());
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

    private long resolveClientId(Long originalId, java.util.Map<Long, Long> clientIdMap) {
        return clientIdMap != null && clientIdMap.containsKey(originalId)
            ? clientIdMap.get(originalId)
            : originalId;
    }

    private record ClientSyncResult(boolean changed, java.util.Map<Long, Long> idMapping) {
        static ClientSyncResult empty() {
            return new ClientSyncResult(false, java.util.Collections.emptyMap());
        }
    }
}

