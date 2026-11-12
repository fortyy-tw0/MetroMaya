package appointment.service;

import java.time.LocalTime;

/** One bookable time on a given day at a given office. */
public record SlotInfo(LocalTime time, int capacity, int remaining, boolean available) {
}
