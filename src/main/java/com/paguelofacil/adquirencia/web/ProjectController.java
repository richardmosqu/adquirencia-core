package com.paguelofacil.adquirencia.web;

import com.paguelofacil.adquirencia.data.DataStore;
import com.paguelofacil.adquirencia.domain.Project;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Proyectos del área: listar, ver, crear y editar (tareas y bitácora incluidas). */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final DataStore store;
    private final AtomicInteger seq = new AtomicInteger(100);

    public ProjectController(DataStore store) {
        this.store = store;
    }

    @GetMapping
    public List<Project> all() {
        return store.projects();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> one(@PathVariable String id) {
        Optional<Project> p = store.projects().stream().filter(x -> x.getId().equals(id)).findFirst();
        return p.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Project create(@RequestBody Project p) {
        p.setId("P-USR-" + seq.incrementAndGet());
        store.projects().add(p);
        return p;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Project> update(@PathVariable String id, @RequestBody Project patch) {
        List<Project> list = store.projects();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) {
                patch.setId(id);
                list.set(i, patch);
                return ResponseEntity.ok(patch);
            }
        }
        return ResponseEntity.notFound().build();
    }
}
