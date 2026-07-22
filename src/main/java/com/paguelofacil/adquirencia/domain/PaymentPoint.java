package com.paguelofacil.adquirencia.domain;

/**
 * POS / dispositivo físico adquirido con un banco. Puede estar sin asignar a un
 * comercio (merchantId null). Es mutable para soportar el alta y la edición.
 */
public class PaymentPoint {

    private String id;
    private String merchantId;   // null = sin asignar a un comercio
    private String bank;         // Towerbank | BAC
    private String deviceType;   // POS Android | POS clásico | mPOS | SoftPOS
    private String serial;
    private String status;       // Operativo | Por instalar | En reparación | Sin conexión

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
}
