package com.arquitectura.dto;

public class SentInvitationSummary {
    private Long canalId;
    private String canalNombre;
    private boolean canalPrivado;
    private Long invitadoId;
    private String invitadoNombre;
    private String estado; // "PENDIENTE"

    public SentInvitationSummary() {
    }

    public SentInvitationSummary(Long canalId, String canalNombre, boolean canalPrivado, Long invitadoId, String invitadoNombre, String estado) {
        this.canalId = canalId;
        this.canalNombre = canalNombre;
        this.canalPrivado = canalPrivado;
        this.invitadoId = invitadoId;
        this.invitadoNombre = invitadoNombre;
        this.estado = estado;
    }

    public Long getCanalId() {
        return canalId;
    }

    public void setCanalId(Long canalId) {
        this.canalId = canalId;
    }

    public String getCanalNombre() {
        return canalNombre;
    }

    public void setCanalNombre(String canalNombre) {
        this.canalNombre = canalNombre;
    }

    public boolean isCanalPrivado() {
        return canalPrivado;
    }

    public void setCanalPrivado(boolean canalPrivado) {
        this.canalPrivado = canalPrivado;
    }

    public Long getInvitadoId() {
        return invitadoId;
    }

    public void setInvitadoId(Long invitadoId) {
        this.invitadoId = invitadoId;
    }

    public String getInvitadoNombre() {
        return invitadoNombre;
    }

    public void setInvitadoNombre(String invitadoNombre) {
        this.invitadoNombre = invitadoNombre;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
