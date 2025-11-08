package com.arquitectura.dto;

public class InviteRequest {

    private Long canalId;
    private String canalUuid;
    private Long invitadoId;
    private Long solicitanteId;

    public InviteRequest() {
    }

    public Long getCanalId() {
        return canalId;
    }

    public void setCanalId(Long canalId) {
        this.canalId = canalId;
    }

    public String getCanalUuid() {
        return canalUuid;
    }

    public void setCanalUuid(String canalUuid) {
        this.canalUuid = canalUuid;
    }

    public Long getInvitadoId() {
        return invitadoId;
    }

    public void setInvitadoId(Long invitadoId) {
        this.invitadoId = invitadoId;
    }

    public Long getSolicitanteId() {
        return solicitanteId;
    }

    public void setSolicitanteId(Long solicitanteId) {
        this.solicitanteId = solicitanteId;
    }
}
