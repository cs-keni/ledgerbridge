package com.ledgerbridge.common.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaFallbackController {

    // Only match paths without dots (frontend routes like /login, /dashboard).
    // Files with extensions (/index.html, /assets/main.js) fall through to the
    // static resource handler — avoiding a forward-re-entry infinite loop.
    @GetMapping({"/{path:[^.]*}", "/**/{path:[^.]*}"})
    public String spa() {
        return "forward:/index.html";
    }
}
