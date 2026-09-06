package su.terrafirmagreg.core.compat.tfc.solar;

public interface ServerCalendarExtension {
    void tfg$skipForwardBy(long calendarTicksToSkip);

    void tfg$setMonthLength(int newMonthLength);

    void tfg$publishCalendarToClients();
}
