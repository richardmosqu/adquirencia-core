package com.paguelofacil.adquirencia.web;

import com.paguelofacil.adquirencia.data.DataStore;
import com.paguelofacil.adquirencia.domain.Credential;
import com.paguelofacil.adquirencia.domain.DocumentItem;
import com.paguelofacil.adquirencia.domain.Merchant;
import com.paguelofacil.adquirencia.domain.PaymentPoint;
import com.paguelofacil.adquirencia.domain.Project;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** Módulos de los tabs laterales: comercios, proyectos, credenciales, puntos de pago y documentos. */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final DataStore store;

    public CatalogController(DataStore store) {
        this.store = store;
    }

    @GetMapping("/merchants")
    public List<Merchant> merchants() {
        return store.merchants();
    }

    @GetMapping("/projects")
    public List<Project> projects() {
        return store.projects();
    }

    @GetMapping("/payment-points")
    public List<PaymentPoint> paymentPoints() {
        return store.paymentPoints();
    }

    @GetMapping("/documents")
    public List<DocumentItem> documents() {
        return store.documents();
    }

    @GetMapping("/dr-codes")
    public Object drCodes() {
        return DataStore.DR_CODES;
    }
}
