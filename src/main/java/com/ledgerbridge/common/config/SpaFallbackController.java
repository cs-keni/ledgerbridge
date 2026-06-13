package com.ledgerbridge.common.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaFallbackController {

    // Match root and single-segment extensionless paths (/login, /dashboard, /admin).
    // PathPatternParser forbids anything after **, so /**/{path} is invalid.
    // The forward to /index.html does not loop because "index.html" contains a
    // dot and does not match [^.]*; the static resource handler serves it directly.
    @GetMapping({"", "/", "/{path:[^.]*}"})
    public String spa() {
        return "forward:/index.html";
    }
}
