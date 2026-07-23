package com.paguelofacil.brujula.domain;

/** Ángulo de venta sugerido según el canal del comercio. */
public class SalesAngle {
    private String id;
    private String canal;    // Físico | En línea | Mixto | Cualquiera
    private String angulo;

    public SalesAngle() {}
    public SalesAngle(String id, String canal, String angulo) {
        this.id = id; this.canal = canal; this.angulo = angulo;
    }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public String getAngulo() { return angulo; }
    public void setAngulo(String angulo) { this.angulo = angulo; }
}
