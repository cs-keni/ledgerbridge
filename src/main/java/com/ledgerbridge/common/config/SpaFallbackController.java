package com.ledgerbridge.common.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class SpaFallbackController {

    @GetMapping("/{*path}")
    public String forward(@PathVariable String path) {
        if (path.contains(".")
                || path.startsWith("/api/")
                || path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")) {
            return null;
        }
        return "forward:/index.html";
    }
}
