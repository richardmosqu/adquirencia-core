package com.paguelofacil.adquirencia.web;

import com.paguelofacil.adquirencia.data.DataStore;
import com.paguelofacil.adquirencia.domain.Credential;
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

/** Ciclo de vida de las credenciales / DBAs: listar, ver, crear y editar. */
@RestController
@RequestMapping("/api/credentials")
public class CredentialController {

    private final DataStore store;
    private final AtomicInteger seq = new AtomicInteger(100);

    public CredentialController(DataStore store) {
        this.store = store;
    }

    @GetMapping
    public List<Credential> all() {
        return store.credentials();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Credential> one(@PathVariable String id) {
        return find(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Credential create(@RequestBody Credential c) {
        c.setId("C-USR-" + seq.incrementAndGet());
        store.credentials().add(c);
        return c;
    }

    /** Reemplaza el registro (edición del formulario o avance de estado/pruebas). */
    @PutMapping("/{id}")
    public ResponseEntity<Credential> update(@PathVariable String id, @RequestBody Credential patch) {
        List<Credential> list = store.credentials();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) {
                patch.setId(id);
                list.set(i, patch);
                return ResponseEntity.ok(patch);
            }
        }
        return ResponseEntity.notFound().build();
    }

    private Optional<Credential> find(String id) {
        return store.credentials().stream().filter(c -> c.getId().equals(id)).findFirst();
    }
}
