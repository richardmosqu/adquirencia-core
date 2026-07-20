package com.paguelofacil.adquirencia.domain;

import java.time.LocalDateTime;

/** Credencial / DBA emitida por un procesador para un comercio. */
public class Credential {

    private String id;
    private String merchantId;
    private Processor processor;
    private String dba;
    private String credentialType;  // MID | TID | API Key | Terminal 3DS
    private String status;          // Activa | Pendiente | Vencida | Error de configuración
    private LocalDateTime lastSentAt;

    public Credential() {
    }

    public Credential(String id, String merchantId, Processor processor, String dba,
                      String credentialType, String status, LocalDateTime lastSentAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.processor = processor;
        this.dba = dba;
        this.credentialType = credentialType;
        this.status = status;
        this.lastSentAt = lastSentAt;
    }

    public String getId() { return id; }
    public String getMerchantId() { return merchantId; }
    public Processor getProcessor() { return processor; }
    public String getDba() { return dba; }
    public String getCredentialType() { return credentialType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(LocalDateTime lastSentAt) { this.lastSentAt = lastSentAt; }
}
