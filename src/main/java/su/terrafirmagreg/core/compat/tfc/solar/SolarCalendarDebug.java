package su.terrafirmagreg.core.compat.tfc.solar;

import su.terrafirmagreg.core.TFGCore;
import su.terrafirmagreg.core.config.TFGConfig;

public final class SolarCalendarDebug {
    private SolarCalendarDebug() {
    }

    public static void log(String message, Object... args) {
        if (!TFGConfig.SERVER.solarCalendarDebugLogging.get()) {
            return;
        }
        if (args.length == 0) {
            TFGCore.LOGGER.info("[TFG Solar] {}", message);
        } else {
            TFGCore.LOGGER.info("[TFG Solar] " + message, args);
        }
    }
}
