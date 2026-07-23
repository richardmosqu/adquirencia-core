package com.paguelofacil.adquirencia.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Iniciativa del área de adquirencia, con responsables, fechas, prioridad,
 * progreso, checklist de tareas y bitácora de seguimiento. Es mutable para
 * soportar el alta y la edición; las tareas y la bitácora se guardan enteras.
 */
public class Project {

    private String id;
    private String name;
    private String description;
    private List<String> responsables = new ArrayList<>();
    private LocalDate fechaInicio;
    private LocalDate fechaEntrega;
    private String status;       // Planeado | En progreso | En pausa | Completado | Cancelado
    private String prioridad;    // Baja | Media | Alta
    private int progreso;        // 0..100 (manual; si hay tareas se deriva en el front)
    private String merchantId;   // opcional: si el proyecto se relaciona a un comercio
    private List<ProjectTask> tasks = new ArrayList<>();
    private List<ProjectLog> seguimiento = new ArrayList<>();

    public Project() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getResponsables() { return responsables; }
    public void setResponsables(List<String> responsables) { this.responsables = responsables != null ? responsables : new ArrayList<>(); }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDate fechaEntrega) { this.fechaEntrega = fechaEntrega; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }
    public int getProgreso() { return progreso; }
    public void setProgreso(int progreso) { this.progreso = progreso; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public List<ProjectTask> getTasks() { return tasks; }
    public void setTasks(List<ProjectTask> tasks) { this.tasks = tasks != null ? tasks : new ArrayList<>(); }
    public List<ProjectLog> getSeguimiento() { return seguimiento; }
    public void setSeguimiento(List<ProjectLog> seguimiento) { this.seguimiento = seguimiento != null ? seguimiento : new ArrayList<>(); }
}
