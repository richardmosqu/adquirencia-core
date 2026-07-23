package com.paguelofacil.brujula.domain;

/** Rubro / giro de negocio con su elegibilidad (permitido o no). */
public class Rubro {
    private String id;
    private String name;
    private boolean allowed;   // true = permitido, false = no permitido
    private String nota;

    public Rubro() {}
    public Rubro(String id, String name, boolean allowed, String nota) {
        this.id = id; this.name = name; this.allowed = allowed; this.nota = nota;
    }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }
    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }
}
