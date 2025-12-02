package com.arquitectura.controladores.conexion;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Snapshot serializable que representa el estado de una sesión activa en un servidor.
 * Se utiliza para sincronizar información entre servidores pares.
 */
public class RemoteSessionSnapshot {

    private String serverId;
    private String sessionId;
    private Long clienteId;
    private String usuario;
    private String email; // Email para identificación global entre servidores
    private String ip;
    private Set<Long> canales = new HashSet<>();
    private Map<Long, String> channelUuids = new HashMap<>();

    public RemoteSessionSnapshot() {
    }

    public RemoteSessionSnapshot(String serverId,
                                 String sessionId,
                                 Long clienteId,
                                 String usuario,
                                 String ip,
                                 Set<Long> canales) {
        this.serverId = serverId;
        this.sessionId = sessionId;
        this.clienteId = clienteId;
        this.usuario = usuario;
        this.ip = ip;
        if (canales != null) {
            this.canales = new HashSet<>(canales);
        }
    }
    
    public RemoteSessionSnapshot(String serverId,
                                 String sessionId,
                                 Long clienteId,
                                 String usuario,
                                 String email,
                                 String ip,
                                 Set<Long> canales) {
        this(serverId, sessionId, clienteId, usuario, ip, canales);
        this.email = email;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Set<Long> getCanales() {
        return canales;
    }

    public void setCanales(Set<Long> canales) {
        this.canales = canales != null ? new HashSet<>(canales) : new HashSet<>();
    }

    public Map<Long, String> getChannelUuids() {
        return channelUuids;
    }

    public void setChannelUuids(Map<Long, String> channelUuids) {
        this.channelUuids = channelUuids != null ? new HashMap<>(channelUuids) : new HashMap<>();
    }

    @Override
    public String toString() {
        return "RemoteSessionSnapshot{" +
            "serverId='" + serverId + '\'' +
            ", sessionId='" + sessionId + '\'' +
            ", clienteId=" + clienteId +
            ", usuario='" + usuario + '\'' +
            ", email='" + email + '\'' +
            ", canales=" + canales +
            '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverId, sessionId, clienteId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        RemoteSessionSnapshot other = (RemoteSessionSnapshot) obj;
        return Objects.equals(serverId, other.serverId)
            && Objects.equals(sessionId, other.sessionId)
            && Objects.equals(clienteId, other.clienteId);
    }
}
