package appointment.service;

import java.util.Map;

/**
 * Each city's SAMPLE fee multiplier, the same values as NCR_CITIES' `rate` in static/js/common.js (keep the two
 * in sync). The front end scales the displayed fees with it; the server uses it so the amount charged in the demo
 * payment is exactly the total the applicant saw, without trusting an amount sent by the browser.
 */
public final class SampleCityRates {

    // ponytail: duplicated from common.js; move both to one /api source if the rates ever change often
    private static final Map<String, Double> RATES = Map.ofEntries(
            Map.entry("caloocan", 0.95), Map.entry("las-pinas", 1.00), Map.entry("makati", 1.30),
            Map.entry("malabon", 0.90), Map.entry("mandaluyong", 1.15), Map.entry("manila", 1.15),
            Map.entry("marikina", 1.00), Map.entry("muntinlupa", 1.10), Map.entry("navotas", 0.90),
            Map.entry("paranaque", 1.10), Map.entry("pasay", 1.10), Map.entry("pasig", 1.20),
            Map.entry("pateros", 0.85), Map.entry("quezon-city", 1.15), Map.entry("san-juan", 1.10),
            Map.entry("taguig", 1.25), Map.entry("valenzuela", 0.95));

    private SampleCityRates() { }

    public static double of(String officeId) {
        return RATES.getOrDefault(officeId, 1.0);
    }
}
