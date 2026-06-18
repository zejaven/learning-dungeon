package com.interviewlearning.api;

import com.interviewlearning.styles.StyleRepository;
import com.interviewlearning.styles.StyleRepository.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CRUD for the user's saved generation styles. */
@RestController
@RequestMapping("/api/styles")
public class StyleController {

    private static final Logger log = LoggerFactory.getLogger(StyleController.class);

    private final StyleRepository styles;

    public StyleController(StyleRepository styles) {
        this.styles = styles;
    }

    @GetMapping
    public List<Style> list() {
        try {
            return styles.list();
        } catch (RuntimeException e) {
            log.warn("Could not list styles: {}", e.getMessage());
            return List.of();
        }
    }

    public record SaveRequest(String name, String instruction) {
    }

    @PostMapping
    public void save(@RequestBody SaveRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            return;
        }
        styles.save(request.name().trim(),
                request.instruction() == null ? "" : request.instruction().trim());
    }

    @DeleteMapping("/{name}")
    public void delete(@PathVariable String name) {
        styles.delete(name);
    }
}
