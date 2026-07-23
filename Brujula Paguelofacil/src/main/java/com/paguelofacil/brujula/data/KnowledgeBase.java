package com.paguelofacil.brujula.data;

import com.paguelofacil.brujula.domain.Objection;
import com.paguelofacil.brujula.domain.PfService;
import com.paguelofacil.brujula.domain.Rubro;
import com.paguelofacil.brujula.domain.SalesAngle;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base de conocimiento comercial de Brújula PF, en memoria y editable:
 * rubros permitidos/no permitidos, catálogo de servicios PF, ángulos de venta
 * y objeciones. Se siembra con datos de ejemplo y se puede reemplazar desde la
 * pantalla de administración.
 */
@Component
public class KnowledgeBase {

    private final List<Rubro> rubros = new CopyOnWriteArrayList<>();
    private final List<PfService> services = new CopyOnWriteArrayList<>();
    private final List<SalesAngle> angles = new CopyOnWriteArrayList<>();
    private final List<Objection> objections = new CopyOnWriteArrayList<>();

    @PostConstruct
    void seed() {
        // Rubros permitidos
        rubros.add(new Rubro("R-1", "Retail / Comercio", true, "Venta de productos al detalle."));
        rubros.add(new Rubro("R-2", "Restaurantes y comida", true, "Alimentos y bebidas."));
        rubros.add(new Rubro("R-3", "Hotelería y turismo", true, "Hoteles, agencias y tours."));
        rubros.add(new Rubro("R-4", "Salud y farmacia", true, "Clínicas, consultorios y farmacias."));
        rubros.add(new Rubro("R-5", "Educación", true, "Colegios, academias y cursos."));
        rubros.add(new Rubro("R-6", "Servicios profesionales", true, "Consultoría, legal, contable, etc."));
        rubros.add(new Rubro("R-7", "Suscripciones / Membresías", true, "Modelos de cobro recurrente."));
        rubros.add(new Rubro("R-8", "Tecnología / SaaS", true, "Software y servicios digitales."));
        rubros.add(new Rubro("R-9", "Belleza y estética", true, "Salones, spas y estética."));
        rubros.add(new Rubro("R-10", "Deporte y fitness", true, "Gimnasios y estudios."));
        // Rubros no permitidos
        rubros.add(new Rubro("R-90", "Apuestas / Casino", false, "Riesgo regulatorio; no permitido."));
        rubros.add(new Rubro("R-91", "Criptomonedas", false, "Fuera de la política de riesgo."));
        rubros.add(new Rubro("R-92", "Armas y municiones", false, "Actividad restringida."));
        rubros.add(new Rubro("R-93", "Contenido adulto", false, "No permitido por marca/marcas de tarjeta."));
        rubros.add(new Rubro("R-94", "Sustancias controladas", false, "Requiere licencias especiales; no permitido."));
        rubros.add(new Rubro("R-95", "Multinivel / piramidal", false, "Modelo de negocio no permitido."));

        // Catálogo de servicios PF
        services.add(new PfService("S-1", "AUTH_CAPTURE", "Auth/Capture",
                "Autoriza y captura el cobro de una tarjeta de forma directa.",
                List.of(), List.of(), List.of("cobro", "autorizar", "presencial"), 12));
        services.add(new PfService("S-2", "CHECKOUT", "Checkout",
                "Página de pago lista para tu tienda en línea.",
                List.of("En línea", "Mixto"), List.of(), List.of("tienda", "web", "ecommerce", "carrito"), 14));
        services.add(new PfService("S-3", "LINK_PAGO", "Link de pago",
                "Cobra con un enlace, sin necesidad de tener sitio web.",
                List.of(), List.of("Pequeño", "Mediano"), List.of("rápido", "simple", "sin web", "enlace", "redes"), 13));
        services.add(new PfService("S-4", "RECURRENCIA", "Recurrencia",
                "Cobros automáticos periódicos para suscripciones y membresías.",
                List.of("En línea", "Mixto"), List.of(), List.of("suscripción", "recurrente", "membresía", "mensualidad"), 11));
        services.add(new PfService("S-5", "API", "API",
                "Integración a medida con tu plataforma o sistemas.",
                List.of("En línea", "Mixto"), List.of("Mediano", "Grande"), List.of("integrar", "api", "desarrollo", "plataforma", "automatizar"), 10));
        services.add(new PfService("S-6", "POS", "POS / Terminal",
                "Cobra de forma presencial con terminal o SoftPOS.",
                List.of("Físico", "Mixto"), List.of(), List.of("presencial", "terminal", "local", "física"), 12));

        // Ángulos de venta por canal
        angles.add(new SalesAngle("A-1", "Físico",
                "Digitaliza el cobro presencial con POS y suma un link de pago para no perder ventas fuera del local."));
        angles.add(new SalesAngle("A-2", "En línea",
                "Activa cobros en línea en minutos (Checkout o Link de pago) y automatiza la recurrencia para ingresos predecibles."));
        angles.add(new SalesAngle("A-3", "Mixto",
                "Unifica el cobro físico y en línea en una sola plataforma, con reportería y conciliación centralizadas."));
        angles.add(new SalesAngle("A-9", "Cualquiera",
                "PagueloFacil te da el medio de cobro correcto para tu operación, con soporte local y activación rápida."));

        // Objeciones esperadas y su respuesta
        objections.add(new Objection("O-1", "Cualquiera", "Ya trabajo con otro proveedor.",
                "Te acompañamos en la migración sin fricción y comparamos costos y aprobación: normalmente mejoramos ambos."));
        objections.add(new Objection("O-2", "En línea", "Me preocupa el fraude en línea.",
                "Incluimos 3DS y reglas de riesgo para proteger cada transacción sin sacrificar la aprobación."));
        objections.add(new Objection("O-3", "Pequeño", "Creo que es caro para mi tamaño.",
                "Empiezas sin costos fijos altos: pagas por transacción y creces a tu ritmo."));
        objections.add(new Objection("O-4", "Físico", "Ya tengo terminal del banco.",
                "Sumamos cobro en línea y link de pago para vender también fuera del mostrador, con una sola conciliación."));
        objections.add(new Objection("O-5", "Grande", "Necesito integración con mis sistemas.",
                "Nuestra API y webhooks se integran a tu plataforma, con soporte técnico dedicado."));
        objections.add(new Objection("O-6", "Mediano", "No tengo equipo técnico.",
                "Con Checkout y Link de pago no necesitas desarrollo; se activa y se usa el mismo día."));
    }

    public List<Rubro> getRubros() { return rubros; }
    public List<PfService> getServices() { return services; }
    public List<SalesAngle> getAngles() { return angles; }
    public List<Objection> getObjections() { return objections; }

    public void replaceRubros(List<Rubro> v) { rubros.clear(); if (v != null) rubros.addAll(v); }
    public void replaceServices(List<PfService> v) { services.clear(); if (v != null) services.addAll(v); }
    public void replaceAngles(List<SalesAngle> v) { angles.clear(); if (v != null) angles.addAll(v); }
    public void replaceObjections(List<Objection> v) { objections.clear(); if (v != null) objections.addAll(v); }
}
