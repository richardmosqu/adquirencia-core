package com.paguelofacil.brujula.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio del catálogo de PagueloFacil con las condiciones/criterios que
 * indican cuándo encaja (por canal, tamaño y necesidad).
 */
public class PfService {
    private String id;
    private String code;          // AUTH_CAPTURE | CHECKOUT | LINK_PAGO | RECURRENCIA | API | POS
    private String name;
    private String descripcion;
    private List<String> canales = new ArrayList<>();   // Físico | En línea | Mixto (vacío = todos)
    private List<String> tamanos = new ArrayList<>();   // Pequeño | Mediano | Grande (vacío = todos)
    private List<String> needs = new ArrayList<>();      // palabras clave de necesidad que lo potencian
    private int peso = 10;                                // peso base para el ranking

    public PfService() {}
    public PfService(String id, String code, String name, String descripcion,
                     List<String> canales, List<String> tamanos, List<String> needs, int peso) {
        this.id = id; this.code = code; this.name = name; this.descripcion = descripcion;
        setCanales(canales); setTamanos(tamanos); setNeeds(needs); this.peso = peso;
    }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public List<String> getCanales() { return canales; }
    public void setCanales(List<String> canales) { this.canales = canales != null ? canales : new ArrayList<>(); }
    public List<String> getTamanos() { return tamanos; }
    public void setTamanos(List<String> tamanos) { this.tamanos = tamanos != null ? tamanos : new ArrayList<>(); }
    public List<String> getNeeds() { return needs; }
    public void setNeeds(List<String> needs) { this.needs = needs != null ? needs : new ArrayList<>(); }
    public int getPeso() { return peso; }
    public void setPeso(int peso) { this.peso = peso; }
}
