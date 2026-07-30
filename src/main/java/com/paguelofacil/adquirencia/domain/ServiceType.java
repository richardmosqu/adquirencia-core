package com.paguelofacil.adquirencia.domain;

/** Tipo de servicio / producto por el que entra la transacción. */
public enum ServiceType {
    AUTH_CAPTURE("Auth/Capture"),
    RECURRENCIA("Recurrencia"),
    LINK_PAGO("Link de pago"),
    API("API");

    private final String label;

    ServiceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
