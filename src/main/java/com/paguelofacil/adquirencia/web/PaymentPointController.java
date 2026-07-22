package com.paguelofacil.adquirencia.web;

import com.paguelofacil.adquirencia.data.DataStore;
import com.paguelofacil.adquirencia.domain.PaymentPoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Puntos de pago (POS): listar, agregar y editar. Un POS puede quedar sin asignar. */
@RestController
@RequestMapping("/api/payment-points")
public class PaymentPointController {

    private final DataStore store;
    private final AtomicInteger seq = new AtomicInteger(100);

    public PaymentPointController(DataStore store) {
        this.store = store;
    }

    @GetMapping
    public List<PaymentPoint> all() {
        return store.paymentPoints();
    }

    @PostMapping
    public PaymentPoint create(@RequestBody PaymentPoint p) {
        p.setId("PP-USR-" + seq.incrementAndGet());
        store.paymentPoints().add(p);
        return p;
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentPoint> update(@PathVariable String id, @RequestBody PaymentPoint patch) {
        List<PaymentPoint> list = store.paymentPoints();
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
