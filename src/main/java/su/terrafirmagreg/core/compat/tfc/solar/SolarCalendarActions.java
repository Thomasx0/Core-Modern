package su.terrafirmagreg.core.compat.tfc.solar;

import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ServerCalendar;

public final class SolarCalendarActions {
    private SolarCalendarActions() {
    }

    public static void skipForwardBy(long calendarTicks) {
        SolarCalendarDebug.log("skipForwardBy called with {} ticks (server calendar before={})",
                calendarTicks, Calendars.SERVER.getCalendarTicks());
        try {
            asServerExtension(Calendars.SERVER).tfg$skipForwardBy(calendarTicks);
            SolarCalendarDebug.log("skipForwardBy finished (server calendar after={})", Calendars.SERVER.getCalendarTicks());
        } catch (Throwable error) {
            SolarCalendarDebug.log("skipForwardBy extension call failed: {}, falling back to setTimeFromCalendarTime",
                    error.toString());
            Calendars.SERVER.setTimeFromCalendarTime(Calendars.SERVER.getCalendarTicks() + calendarTicks);
            SolarCalendarDebug.log("skipForwardBy fallback finished (server calendar after={})",
                    Calendars.SERVER.getCalendarTicks());
        }
    }

    public static void setCalendarTickRate(float calendarTickRate) {
        asCalendarExtension(Calendars.SERVER).tfg$setCalendarTickRate(calendarTickRate);
        asServerExtension(Calendars.SERVER).tfg$publishCalendarToClients();
    }

    public static void setMonthLength(int newMonthLength) {
        asServerExtension(Calendars.SERVER).tfg$setMonthLength(newMonthLength);
    }

    public static ServerCalendar server() {
        return Calendars.SERVER;
    }

    private static ServerCalendarExtension asServerExtension(ServerCalendar calendar) {
        return (ServerCalendarExtension) (Object) calendar;
    }

    private static CalendarExtension asCalendarExtension(ServerCalendar calendar) {
        return (CalendarExtension) (Object) calendar;
    }
}
