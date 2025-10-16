package com.arquitectura.dto;

public class InvitationSummary {
    private Long canalId;
    private String canalNombre;
    private boolean canalPrivado;
    private Long invitadorId;
    private String invitadorNombre;

    public InvitationSummary() {
    }

    public InvitationSummary(Long canalId, String canalNombre, boolean canalPrivado, Long invitadorId, String invitadorNombre) {
        this.canalId = canalId;
        this.canalNombre = canalNombre;
        this.canalPrivado = canalPrivado;
        this.invitadorId = invitadorId;
        this.invitadorNombre = invitadorNombre;
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

    public Long getInvitadorId() {
        return invitadorId;
    }

    public void setInvitadorId(Long invitadorId) {
        this.invitadorId = invitadorId;
    }

    public String getInvitadorNombre() {
        return invitadorNombre;
    }

    public void setInvitadorNombre(String invitadorNombre) {
        this.invitadorNombre = invitadorNombre;
    }
}
