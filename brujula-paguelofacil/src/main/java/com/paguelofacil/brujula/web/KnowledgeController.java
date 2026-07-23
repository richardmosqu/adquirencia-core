package com.paguelofacil.brujula.web;

import com.paguelofacil.brujula.data.KnowledgeBase;
import com.paguelofacil.brujula.domain.Objection;
import com.paguelofacil.brujula.domain.PfService;
import com.paguelofacil.brujula.domain.Rubro;
import com.paguelofacil.brujula.domain.SalesAngle;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Lee y edita la base de conocimiento (rubros, servicios, ángulos, objeciones). */
@RestController
@RequestMapping("/api/kb")
public class KnowledgeController {

    private final KnowledgeBase kb;

    public KnowledgeController(KnowledgeBase kb) {
        this.kb = kb;
    }

    @GetMapping
    public Map<String, Object> all() {
        return Map.of(
                "rubros", kb.getRubros(),
                "services", kb.getServices(),
                "angles", kb.getAngles(),
                "objections", kb.getObjections());
    }

    @PutMapping("/rubros")
    public List<Rubro> rubros(@RequestBody List<Rubro> v) { kb.replaceRubros(v); return kb.getRubros(); }

    @PutMapping("/services")
    public List<PfService> services(@RequestBody List<PfService> v) { kb.replaceServices(v); return kb.getServices(); }

    @PutMapping("/angles")
    public List<SalesAngle> angles(@RequestBody List<SalesAngle> v) { kb.replaceAngles(v); return kb.getAngles(); }

    @PutMapping("/objections")
    public List<Objection> objections(@RequestBody List<Objection> v) { kb.replaceObjections(v); return kb.getObjections(); }
}
