package com.ledgerbridge.common.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class SpaFallbackController {

    // /{*path} is the Spring Boot 3 / PathPatternParser-compatible way to match
    // all paths greedily. We exclude paths with dots so static assets (/main.js,
    // /main.css) fall through to the default resource handler.
    @GetMapping("/{*path}")
    public String forward(@PathVariable String path) {
        if (path.contains(".")) {
            return null;
        }
        return "forward:/index.html";
    }
}
