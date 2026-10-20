package dev.slotly.storefront;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The staff dashboard is a single-page app built into static/app. Its client-side routes are listed
 * explicitly and all serve index.html, so a reload or a shared link to /app/staff still works;
 * asset files under /app/assets/ fall through to the static resource handler.
 */
@Controller
public class SpaController {

    @GetMapping({"/app", "/app/", "/app/login", "/app/register", "/app/bookings", "/app/staff", "/app/services"})
    public String dashboard() {
        return "forward:/app/index.html";
    }
}
