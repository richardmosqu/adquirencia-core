package com.paguelofacil.adquirencia.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * POS / dispositivo físico adquirido con un banco. Puede estar sin asignar a un
 * comercio (merchantId null). Es mutable para soportar el alta y la edición.
 *
 * <p>Cada vez que el POS cambia de comercio se cierra la asignación vigente y se
 * abre una nueva en {@link #getAssignmentHistory()}, de modo que siempre se sabe
 * a quién estuvo asignado antes.</p>
 */
public class PaymentPoint {

    /** Periodo en que el POS estuvo asignado a un comercio. hasta = null → vigente. */
    public static class Assignment {

        private String merchantId;
        private LocalDate desde;
        private LocalDate hasta;

        public Assignment() {
        }

        public Assignment(String merchantId, LocalDate desde, LocalDate hasta) {
            this.merchantId = merchantId;
            this.desde = desde;
            this.hasta = hasta;
        }

        public String getMerchantId() { return merchantId; }
        public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
        public LocalDate getDesde() { return desde; }
        public void setDesde(LocalDate desde) { this.desde = desde; }
        public LocalDate getHasta() { return hasta; }
        public void setHasta(LocalDate hasta) { this.hasta = hasta; }
    }

    private String id;
    private String merchantId;   // null = sin asignar a un comercio
    private String bank;         // Towerbank | BAC
    private String deviceType;   // texto libre: POS Android, mPOS, SoftPOS…
    private String serial;
    private String status;       // Operativo | Por instalar | En reparación | Sin conexión
    private List<Assignment> assignmentHistory = new ArrayList<>();

    public PaymentPoint() {
    }

    public PaymentPoint(String id, String merchantId, String bank, String deviceType,
                        String serial, String status) {
        this.id = id;
        this.merchantId = merchantId;
        this.bank = bank;
        this.deviceType = deviceType;
        this.serial = serial;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getSerial() { return serial; }
    public void setSerial(String serial) { this.serial = serial; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<Assignment> getAssignmentHistory() { return assignmentHistory; }

    public void setAssignmentHistory(List<Assignment> assignmentHistory) {
        this.assignmentHistory = assignmentHistory == null ? new ArrayList<>() : assignmentHistory;
    }
}
