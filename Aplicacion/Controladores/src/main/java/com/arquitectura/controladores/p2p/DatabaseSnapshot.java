package com.arquitectura.controladores.p2p;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa un volcado de la información relevante de la base de datos
 * que se intercambia entre servidores pares para mantener sus estados
 * sincronizados.
 */
public class DatabaseSnapshot {

    private List<ClienteRecord> clientes = new ArrayList<>();
    private List<CanalRecord> canales = new ArrayList<>();
    private List<ChannelMembershipRecord> canalMiembros = new ArrayList<>();
    private List<MensajeRecord> mensajes = new ArrayList<>();

    public List<ClienteRecord> getClientes() {
        return clientes;
    }

    public void setClientes(List<ClienteRecord> clientes) {
        this.clientes = clientes != null ? new ArrayList<>(clientes) : new ArrayList<>();
    }

    public List<CanalRecord> getCanales() {
        return canales;
    }

    public void setCanales(List<CanalRecord> canales) {
        this.canales = canales != null ? new ArrayList<>(canales) : new ArrayList<>();
    }

    public List<ChannelMembershipRecord> getCanalMiembros() {
        return canalMiembros;
    }

    public void setCanalMiembros(List<ChannelMembershipRecord> canalMiembros) {
        this.canalMiembros = canalMiembros != null ? new ArrayList<>(canalMiembros) : new ArrayList<>();
    }

    public List<MensajeRecord> getMensajes() {
        return mensajes;
    }

    public void setMensajes(List<MensajeRecord> mensajes) {
        this.mensajes = mensajes != null ? new ArrayList<>(mensajes) : new ArrayList<>();
    }

    public boolean isEmpty() {
        return clientes.isEmpty() && canales.isEmpty() && canalMiembros.isEmpty() && mensajes.isEmpty();
    }

    public static final class ClienteRecord {
        private Long id;
        private String usuario;
        private String email;
        private String contrasenia;
        private String fotoBase64;
        private String ip;
        private Boolean estado;

        public ClienteRecord() {
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
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

        public String getContrasenia() {
            return contrasenia;
        }

        public void setContrasenia(String contrasenia) {
            this.contrasenia = contrasenia;
        }

        public String getFotoBase64() {
            return fotoBase64;
        }

        public void setFotoBase64(String fotoBase64) {
            this.fotoBase64 = fotoBase64;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public Boolean getEstado() {
            return estado;
        }

        public void setEstado(Boolean estado) {
            this.estado = estado;
        }
    }

    public static final class CanalRecord {
        private Long id;
        private String nombre;
        private Boolean privado;

        public CanalRecord() {
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getNombre() {
            return nombre;
        }

        public void setNombre(String nombre) {
            this.nombre = nombre;
        }

        public Boolean getPrivado() {
            return privado;
        }

        public void setPrivado(Boolean privado) {
            this.privado = privado;
        }
    }

    public static final class ChannelMembershipRecord {
        private Long canalId;
        private Long clienteId;

        public ChannelMembershipRecord() {
        }

        public Long getCanalId() {
            return canalId;
        }

        public void setCanalId(Long canalId) {
            this.canalId = canalId;
        }

        public Long getClienteId() {
            return clienteId;
        }

        public void setClienteId(Long clienteId) {
            this.clienteId = clienteId;
        }
    }

    public static final class MensajeRecord {
        private Long id;
        private String timestamp;
        private String tipo;
        private Long emisorId;
        private Long receptorId;
        private Long canalId;
        private String contenido;
        private String rutaArchivo;
        private String mime;
        private Integer duracionSeg;
        private String transcripcion;

        public MensajeRecord() {
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getTipo() {
            return tipo;
        }

        public void setTipo(String tipo) {
            this.tipo = tipo;
        }

        public Long getEmisorId() {
            return emisorId;
        }

        public void setEmisorId(Long emisorId) {
            this.emisorId = emisorId;
        }

        public Long getReceptorId() {
            return receptorId;
        }

        public void setReceptorId(Long receptorId) {
            this.receptorId = receptorId;
        }

        public Long getCanalId() {
            return canalId;
        }

        public void setCanalId(Long canalId) {
            this.canalId = canalId;
        }

        public String getContenido() {
            return contenido;
        }

        public void setContenido(String contenido) {
            this.contenido = contenido;
        }

        public String getRutaArchivo() {
            return rutaArchivo;
        }

        public void setRutaArchivo(String rutaArchivo) {
            this.rutaArchivo = rutaArchivo;
        }

        public String getMime() {
            return mime;
        }

        public void setMime(String mime) {
            this.mime = mime;
        }

        public Integer getDuracionSeg() {
            return duracionSeg;
        }

        public void setDuracionSeg(Integer duracionSeg) {
            this.duracionSeg = duracionSeg;
        }

        public String getTranscripcion() {
            return transcripcion;
        }

        public void setTranscripcion(String transcripcion) {
            this.transcripcion = transcripcion;
        }
    }
}

