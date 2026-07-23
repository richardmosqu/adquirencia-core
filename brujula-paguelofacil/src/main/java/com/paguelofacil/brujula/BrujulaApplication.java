package com.paguelofacil.brujula;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Brújula PF — asistente de evaluación de leads del área comercial.
 *
 * <p>App Spring Boot totalmente independiente del core de adquirencia: su
 * propio paquete, pom, frontend y puerto. Mover esta carpeta a su propio repo
 * deja una app funcional.</p>
 */
@SpringBootApplication
public class BrujulaApplication {
    public static void main(String[] args) {
        SpringApplication.run(BrujulaApplication.class, args);
    }
}
