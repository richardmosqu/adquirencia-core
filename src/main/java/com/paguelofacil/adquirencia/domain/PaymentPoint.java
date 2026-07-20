package com.paguelofacil.adquirencia.domain;

/** POS / dispositivo físico de un comercio, adquirido con un banco. */
public record PaymentPoint(
        String id,
        String merchantId,
        String bank,        // Towerbank | BAC
        String deviceType,  // POS Android | POS clásico | mPOS | SoftPOS
        String serial,
        String status) {    // Operativo | Sin conexión | En reparación | Por instalar
}
