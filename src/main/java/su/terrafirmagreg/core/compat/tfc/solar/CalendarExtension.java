package su.terrafirmagreg.core.compat.tfc.solar;

/**
 * Mixin accessor for {@link net.dries007.tfc.util.calendar.Calendar}.
 * Implemented by {@code CalendarMixin}; prefer {@link SolarCalendarBackport} in application code.
 */
public interface CalendarExtension {
    float tfg$getCalendarTickRate();

    void tfg$setCalendarTickRate(float rate);

    float tfg$getCalendarPartialTick();

    void tfg$setCalendarPartialTick(float partialTick);
}
