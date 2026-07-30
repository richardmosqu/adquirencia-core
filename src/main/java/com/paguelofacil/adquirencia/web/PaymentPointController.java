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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
        p.setAssignmentHistory(new ArrayList<>());
        if (assigned(p.getMerchantId())) {
            p.getAssignmentHistory().add(
                    new PaymentPoint.Assignment(p.getMerchantId(), LocalDate.now(), null));
        }
        store.paymentPoints().add(p);
        return p;
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentPoint> update(@PathVariable String id, @RequestBody PaymentPoint patch) {
        List<PaymentPoint> list = store.paymentPoints();
        for (int i = 0; i < list.size(); i++) {
            PaymentPoint current = list.get(i);
            if (current.getId().equals(id)) {
                patch.setId(id);
                patch.setAssignmentHistory(trackAssignment(current, patch.getMerchantId()));
                list.set(i, patch);
                return ResponseEntity.ok(patch);
            }
        }
        return ResponseEntity.notFound().build();
    }

    private static boolean assigned(String merchantId) {
        return merchantId != null && !merchantId.isBlank();
    }

    /**
     * Historial actualizado del POS: si cambió de comercio, cierra la asignación
     * vigente con la fecha de hoy y abre la nueva. El historial nunca lo manda el
     * cliente, siempre se deriva aquí.
     */
    private static List<PaymentPoint.Assignment> trackAssignment(PaymentPoint current, String next) {
        List<PaymentPoint.Assignment> history = new ArrayList<>(current.getAssignmentHistory());
        String previous = current.getMerchantId();
        if (Objects.equals(previous, next)) {
            return history;
        }
        for (PaymentPoint.Assignment a : history) {
            if (a.getHasta() == null) {
                a.setHasta(LocalDate.now());
            }
        }
        if (assigned(next)) {
            history.add(new PaymentPoint.Assignment(next, LocalDate.now(), null));
        }
        return history;
    }
}
