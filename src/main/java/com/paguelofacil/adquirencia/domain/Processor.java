package com.paguelofacil.adquirencia.domain;

/** Procesadores / canales de adquirencia por los que se enruta el tráfico. */
public enum Processor {
    POWERTRANZ("PowerTranz"),
    EVERTEC("Evertec");

    private final String label;

    Processor(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
