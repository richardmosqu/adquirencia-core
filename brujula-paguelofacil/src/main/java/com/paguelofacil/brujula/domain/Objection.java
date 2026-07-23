package com.paguelofacil.brujula.domain;

/** Objeción esperada del cliente y cómo resolverla. */
public class Objection {
    private String id;
    private String trigger;    // Físico | En línea | Mixto | Pequeño | Mediano | Grande | Cualquiera
    private String objecion;
    private String respuesta;

    public Objection() {}
    public Objection(String id, String trigger, String objecion, String respuesta) {
        this.id = id; this.trigger = trigger; this.objecion = objecion; this.respuesta = respuesta;
    }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTrigger() { return trigger; }
    public void setTrigger(String trigger) { this.trigger = trigger; }
    public String getObjecion() { return objecion; }
    public void setObjecion(String objecion) { this.objecion = objecion; }
    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }
}
