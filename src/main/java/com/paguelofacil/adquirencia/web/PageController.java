package com.paguelofacil.adquirencia.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Rutas de las páginas. La raíz sirve el landing (index.html, estático) y
 * {@code /core} entra a la aplicación del Core de Adquirencia.
 *
 * <p>No hay login: el Core se muestra abierto para poder explorarlo desde
 * cualquier teléfono durante la presentación.</p>
 */
@Controller
public class PageController {

    @GetMapping({"/core", "/core/"})
    public String core() {
        return "forward:/core.html";
    }
}
