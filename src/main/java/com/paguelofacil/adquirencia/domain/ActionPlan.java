package com.paguelofacil.adquirencia.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan de acción: guía operativa que se enlaza a una regla de alerta. Cuando la
 * alerta se dispara, su plan de acción aparece como "Plan de acción recomendado"
 * en el detalle. Es mutable para soportar el CRUD desde la vista de alertas.
 */
public class ActionPlan {

    private String id;
    private String name;
    private String description;      // resumen de una línea
    private List<String> steps = new ArrayList<>();  // pasos concretos

    public ActionPlan() {
    }

    public ActionPlan(String id, String name, String description, List<String> steps) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getSteps() { return steps; }
    public void setSteps(List<String> steps) { this.steps = steps != null ? steps : new ArrayList<>(); }
}
