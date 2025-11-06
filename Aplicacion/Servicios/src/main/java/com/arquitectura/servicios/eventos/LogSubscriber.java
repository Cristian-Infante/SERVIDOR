package com.arquitectura.servicios.eventos;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import com.arquitectura.entidades.ArchivoMensaje;
import com.arquitectura.entidades.AudioMensaje;
import com.arquitectura.entidades.Canal;
import com.arquitectura.entidades.Cliente;
import com.arquitectura.entidades.Log;
import com.arquitectura.entidades.Mensaje;
import com.arquitectura.entidades.TextoMensaje;
import com.arquitectura.repositorios.CanalRepository;
import com.arquitectura.repositorios.ClienteRepository;
import com.arquitectura.repositorios.LogRepository;

public class LogSubscriber implements SessionObserver {

    private static final Logger LOGGER = Logger.getLogger(LogSubscriber.class.getName());

    private final LogRepository logRepository;
    private final ClienteRepository clienteRepository;
    private final CanalRepository canalRepository;

    public LogSubscriber(LogRepository logRepository, ClienteRepository clienteRepository, CanalRepository canalRepository, SessionEventBus bus) {
        this.logRepository = Objects.requireNonNull(logRepository, "logRepository");
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "clienteRepository");
        this.canalRepository = Objects.requireNonNull(canalRepository, "canalRepository");
        bus.subscribe(this);
    }

    @Override
    public void onEvent(SessionEvent event) {
        Log log = new Log();
        log.setFechaHora(event.getTimestamp());
        log.setTipo(Boolean.FALSE);
        log.setDetalle(describir(event));
        logRepository.append(log);
        LOGGER.fine(() -> "Evento registrado: " + event.getType());
    }

    private String describir(SessionEvent event) {
        if (SessionEventTypes.isUserRegistered(event.getType())) {
            return describirRegistro(event);
        }

        return switch (event.getType()) {
            case LOGIN -> describirLogin(event);
            case LOGOUT -> describirLogout(event);
            case TCP_CONNECTED -> describirConexionTCP(event);
            case TCP_DISCONNECTED -> describirDesconexionTCP(event);
            case MESSAGE_SENT -> describirMensaje(event);
            case NEW_MESSAGE -> describirNuevoMensaje(event);
            case NEW_CHANNEL_MESSAGE -> describirNuevoMensajeCanal(event);
            case CHANNEL_CREATED -> describirCanalCreado(event);
            case INVITE_SENT -> describirInvitacion(event);
            case INVITE_ACCEPTED -> describirInvitacionAceptada(event);
            case INVITE_REJECTED -> describirInvitacionRechazada(event);
            case AUDIO_SENT -> describirAudio(event);
            default -> describirDesconocido(event);
        };
    }

    private String describirDesconocido(SessionEvent event) {
        return "Evento " + event.getType();
    }
    
    private String describirConexionTCP(SessionEvent event) {
        if (event.getPayload() instanceof com.arquitectura.servicios.conexion.SessionDescriptor descriptor) {
            return String.format("Nueva conexión TCP establecida - Sesión: %s desde %s",
                    descriptor.getSessionId(),
                    descriptor.getIp() != null ? descriptor.getIp() : "IP desconocida");
        }
        return "Nueva conexión TCP establecida - Sesión: " + event.getSessionId();
    }
    
    private String describirDesconexionTCP(SessionEvent event) {
        if (event.getPayload() instanceof com.arquitectura.servicios.conexion.SessionDescriptor descriptor) {
            String usuario = descriptor.getClienteId() != null 
                ? descriptor.getUsuario() 
                : "Anónimo";
            return String.format("Conexión TCP cerrada - Sesión: %s, Usuario: %s",
                    descriptor.getSessionId(),
                    usuario);
        }
        return "Conexión TCP cerrada - Sesión: " + event.getSessionId();
    }
    
    private String describirNuevoMensaje(SessionEvent event) {
        if (!(event.getPayload() instanceof Mensaje mensaje)) {
            return "Nuevo mensaje recibido (sin detalles)";
        }
        
        String emisor = obtenerNombreUsuario(mensaje.getEmisor());
        String receptor = obtenerNombreUsuario(mensaje.getReceptor());
        
        return String.format("Nuevo mensaje de %s para %s", emisor, receptor);
    }
    
    private String describirNuevoMensajeCanal(SessionEvent event) {
        if (!(event.getPayload() instanceof Mensaje mensaje)) {
            LOGGER.warning("NEW_CHANNEL_MESSAGE: payload no es un Mensaje - " + event.getPayload());
            return "Nuevo mensaje en canal (sin detalles)";
        }
        
        String emisor = obtenerNombreUsuario(mensaje.getEmisor());
        String canal = mensaje.getCanalId() != null 
            ? canalRepository.findById(mensaje.getCanalId())
                .map(c -> c.getNombre())
                .orElse("Canal " + mensaje.getCanalId())
            : "Canal desconocido";
        
        // LOG DETALLADO DEL CONTENIDO - Usar System.out para asegurar que se vea
        System.out.println("🔔 NEW_CHANNEL_MESSAGE DETALLE:");
        System.out.println("   📝 Mensaje ID: " + mensaje.getId());
        System.out.println("   👤 Emisor: " + emisor + " (ID: " + mensaje.getEmisor() + ")");
        System.out.println("   📺 Canal: " + canal + " (ID: " + mensaje.getCanalId() + ")");
        System.out.println("   🕐 Timestamp: " + mensaje.getTimeStamp());
        System.out.println("   📄 Tipo: " + mensaje.getTipo());
        
        // Mostrar contenido específico según el tipo
        if (mensaje instanceof com.arquitectura.entidades.TextoMensaje texto) {
            System.out.println("   💬 Contenido: \"" + texto.getContenido() + "\"");
        } else if (mensaje instanceof com.arquitectura.entidades.AudioMensaje audio) {
            System.out.println("   🎵 Audio: " + audio.getRutaArchivo());
            System.out.println("   📝 Transcripción: \"" + audio.getTranscripcion() + "\"");
            System.out.println("   ⏱️ Duración: " + audio.getDuracionSeg() + "s");
        } else if (mensaje instanceof com.arquitectura.entidades.ArchivoMensaje archivo) {
            System.out.println("   📎 Archivo: " + archivo.getRutaArchivo());
            System.out.println("   📄 MIME: " + archivo.getMime());
        }
            
        return String.format("Nuevo mensaje de %s en canal %s", emisor, canal);
    }

    private String describirLogin(SessionEvent event) {
        if (event.getActorId() == null) {
            return "Login de usuario desconocido";
        }
        
        return clienteRepository.findById(event.getActorId())
            .map(cliente -> String.format("Usuario '%s' (%s) inició sesión desde IP %s",
                    cliente.getNombreDeUsuario(),
                    cliente.getEmail(),
                    cliente.getIp() != null ? cliente.getIp() : "desconocida"))
            .orElse("Login de usuario ID " + event.getActorId() + " (no encontrado)");
    }

    private String describirLogout(SessionEvent event) {
        if (event.getActorId() == null) {
            return "Logout de sesión " + event.getSessionId();
        }

        return clienteRepository.findById(event.getActorId())
            .map(cliente -> String.format("Usuario '%s' (%s) cerró sesión",
                    cliente.getNombreDeUsuario(),
                    cliente.getEmail()))
            .orElse("Logout de usuario ID " + event.getActorId() + " (no encontrado)");
    }

    private String describirRegistro(SessionEvent event) {
        if (!(event.getPayload() instanceof Cliente cliente)) {
            return "Nuevo usuario registrado";
        }
        return String.format("Usuario '%s' (%s) registrado",
            cliente.getNombreDeUsuario(), cliente.getEmail());
    }

    private String describirMensaje(SessionEvent event) {
        if (!(event.getPayload() instanceof Mensaje mensaje)) {
            return "Mensaje enviado (sin detalles)";
        }

        String emisor = mensaje.getEmisor() != null
            ? clienteRepository.findById(mensaje.getEmisor())
                .map(c -> c.getNombreDeUsuario())
                .orElse("Usuario desconocido")
            : "Usuario desconocido";

        if (mensaje instanceof TextoMensaje texto) {
            String destino = obtenerDestino(mensaje);
            String preview = texto.getContenido().length() > 50 
                ? texto.getContenido().substring(0, 50) + "..."
                : texto.getContenido();
            return String.format("Mensaje de texto de '%s' a %s: \"%s\"", emisor, destino, preview);
        }

        if (mensaje instanceof AudioMensaje audio) {
            String destino = obtenerDestino(mensaje);
            return String.format("Mensaje de audio de '%s' a %s (duración: %ds, archivo: %s)",
                    emisor, destino, audio.getDuracionSeg(), audio.getRutaArchivo());
        }

        if (mensaje instanceof ArchivoMensaje archivo) {
            String destino = obtenerDestino(mensaje);
            return String.format("Archivo enviado por '%s' a %s (tipo: %s, archivo: %s)",
                    emisor, destino, archivo.getMime(), archivo.getRutaArchivo());
        }

        return String.format("Mensaje de '%s' enviado", emisor);
    }

    private String describirCanalCreado(SessionEvent event) {
        if (!(event.getPayload() instanceof Canal canal)) {
            return "Canal creado (sin detalles)";
        }

        String creador = event.getActorId() != null
            ? clienteRepository.findById(event.getActorId())
                .map(c -> c.getNombreDeUsuario())
                .orElse("Usuario desconocido")
            : "Usuario desconocido";

        return String.format("Canal '%s' (ID: %d, %s) creado por '%s'",
                canal.getNombre(),
                canal.getId(),
                canal.getPrivado() ? "privado" : "público",
                creador);
    }

    private String describirInvitacion(SessionEvent event) {
        if (!(event.getPayload() instanceof Map<?, ?> payload)) {
            return "Invitación enviada (sin detalles)";
        }

        Long canalId = payload.get("canalId") instanceof Long id ? id : null;
        Long invitadoId = payload.get("invitadoId") instanceof Long id ? id : null;

        String solicitante = event.getActorId() != null
            ? clienteRepository.findById(event.getActorId())
                .map(c -> c.getNombreDeUsuario())
                .orElse("Usuario desconocido")
            : "Usuario desconocido";

        String invitado = invitadoId != null
            ? clienteRepository.findById(invitadoId)
                .map(c -> c.getNombreDeUsuario())
                .orElse("Usuario ID " + invitadoId)
            : "Usuario desconocido";

        String canal = canalId != null
            ? canalRepository.findById(canalId)
                .map(c -> "'" + c.getNombre() + "'")
                .orElse("Canal ID " + canalId)
            : "Canal desconocido";

        return String.format("'%s' invitó a '%s' al canal %s", solicitante, invitado, canal);
    }

    private String describirInvitacionAceptada(SessionEvent event) {
        if (!(event.getPayload() instanceof Map<?, ?> payload)) {
            return "Invitación aceptada (sin detalles)";
        }

        Long canalId = payload.get("canalId") instanceof Long id ? id : null;
        Long invitadorId = payload.get("invitadorId") instanceof Long id ? id : null;

        String invitado = event.getActorId() != null
            ? clienteRepository.findById(event.getActorId())
                .map(c -> c.getNombreDeUsuario())
                .orElse("Usuario desconocido")
            : "Usuario desconocido";

        String invitador = invitadorId != null
            ? clienteRepository.findById(invitadorId)
                .map(c -> c.getNombreDeUsuario())
                .orElse("Usuario ID " + invitadorId)
            : "Usuario desconocido";

        String canal = canalId != null
            ? canalRepository.findById(canalId)
                .map(c -> "'" + c.getNombre() + "'")
                .orElse("Canal ID " + canalId)
            : "Canal desconocido";

        return String.format("'%s' aceptó invitación de '%s' al canal %s", invitado, invitador, canal);
    }

    private String describirInvitacionRechazada(SessionEvent event) {
        if (!(event.getPayload() instanceof Map<?, ?> payload)) {
            return "Invitación rechazada (sin detalles)";
        }

        Long canalId = payload.get("canalId") instanceof Long id ? id : null;
        Long invitadorId = payload.get("invitadorId") instanceof Long id ? id : null;

        String invitado = event.getActorId() != null
            ? clienteRepository.findById(event.getActorId())
                .map(c -> c.getNombreDeUsuario())
                .orElse("Usuario desconocido")
            : "Usuario desconocido";

        String invitador = invitadorId != null
            ? clienteRepository.findById(invitadorId)
                .map(c -> c.getNombreDeUsuario())
                .orElse("Usuario ID " + invitadorId)
            : "Usuario desconocido";

        String canal = canalId != null
            ? canalRepository.findById(canalId)
                .map(c -> "'" + c.getNombre() + "'")
                .orElse("Canal ID " + canalId)
            : "Canal desconocido";

        return String.format("'%s' rechazó invitación de '%s' al canal %s", invitado, invitador, canal);
    }

    private String describirAudio(SessionEvent event) {
        if (!(event.getPayload() instanceof AudioMensaje audio)) {
            return "Audio enviado (sin detalles)";
        }

        String emisor = audio.getEmisor() != null
            ? clienteRepository.findById(audio.getEmisor())
                .map(c -> c.getNombreDeUsuario())
                .orElse("Usuario desconocido")
            : "Usuario desconocido";

        String destino = obtenerDestino(audio);

        String transcripcionInfo = audio.getTranscripcion() != null && !audio.getTranscripcion().isEmpty()
            ? ", transcripción: \"" + audio.getTranscripcion() + "\""
            : "";

        return String.format("Audio de '%s' a %s (duración: %ds, archivo: %s%s)",
                emisor, destino, audio.getDuracionSeg(), audio.getRutaArchivo(), transcripcionInfo);
    }

    private String obtenerDestino(Mensaje mensaje) {
        if (mensaje.getCanalId() != null) {
            return canalRepository.findById(mensaje.getCanalId())
                .map(c -> "canal '" + c.getNombre() + "'")
                .orElse("canal ID " + mensaje.getCanalId());
        }

        if (mensaje.getReceptor() != null) {
            return clienteRepository.findById(mensaje.getReceptor())
                .map(c -> "usuario '" + c.getNombreDeUsuario() + "'")
                .orElse("usuario ID " + mensaje.getReceptor());
        }

        return "destino desconocido";
    }
    
    private String obtenerNombreUsuario(Long usuarioId) {
        if (usuarioId == null) {
            return "Usuario desconocido";
        }
        return clienteRepository.findById(usuarioId)
                .map(c -> c.getNombreDeUsuario())
                .orElse("Usuario ID " + usuarioId);
    }
}