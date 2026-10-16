package dev.slotly.storefront;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the static booking page for any business link; the page reads the slug from its own URL. */
@Controller
public class BookingPageController {

    @GetMapping({"/b/{slug}", "/b/{slug}/"})
    public String bookingPage() {
        return "forward:/booking/index.html";
    }
}
