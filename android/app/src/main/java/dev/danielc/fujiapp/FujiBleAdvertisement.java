package dev.danielc.fujiapp;

import java.util.Collection;
import java.util.Locale;

/** BLE company IDs are independent of USB vendor IDs. */
final class FujiBleAdvertisement {
    static final int COMPANY_ID = 0x04d8;
    static final String SECURE_ADVERTISEMENT = "a9d2b304-e8d6-4902-8336-352b772d7597";
    static final String SECURE_PAIR = "123d8f06-62a1-4935-9322-833c531ee225";
    static final String LEGACY_APP = "117c4142-edd4-4c77-8696-dd18eebb770a";
    static boolean matches(String name, byte[] manufacturer, Collection<String> services) {
        if (name != null && name.toUpperCase(Locale.ROOT).contains("FUJI")) return true;
        if (name != null && (name.toUpperCase(Locale.ROOT).contains("X-E5") || name.toUpperCase(Locale.ROOT).contains("XE5"))) return true;
        // Android removes the two company-ID bytes from manufacturer data.
        if (manufacturer != null && (manufacturer.length == 5 || manufacturer.length == 6)) return true;
        return services != null && (services.contains(SECURE_ADVERTISEMENT) || services.contains(SECURE_PAIR) || services.contains(LEGACY_APP));
    }
}
