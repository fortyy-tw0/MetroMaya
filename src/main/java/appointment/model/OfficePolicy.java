package appointment.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Office-managed booking hours, capacity, local closures and service instructions. */
public record OfficePolicy(String officeId, List<LocalTime> slotTimes, int capacityPerSlot,
                           Map<LocalDate, Closure> closures, Map<String, Checklist> checklists) {
    public record Closure(String kind, String reason) {
        public Closure {
            if (!kind.equals("LOCAL_HOLIDAY") && !kind.equals("EMERGENCY"))
                throw new IllegalArgumentException("Choose local holiday or emergency closure.");
            if (reason == null || reason.isBlank() || reason.length() > 160)
                throw new IllegalArgumentException("Enter a closure reason up to 160 characters.");
            reason = reason.trim();
        }
    }
    public record Checklist(List<String> documents, String instructions, LocalDate updatedAt) {
        public Checklist {
            documents = List.copyOf(documents);
            if (documents.size() > 20 || documents.stream().anyMatch(s -> s.isBlank() || s.length() > 200))
                throw new IllegalArgumentException("Use up to 20 documents, each under 200 characters.");
            if (instructions == null || instructions.length() > 2000)
                throw new IllegalArgumentException("Instructions must be under 2,000 characters.");
            if (updatedAt == null) throw new IllegalArgumentException("Checklist needs an update date.");
            instructions = instructions.trim();
        }
    }

    public OfficePolicy {
        slotTimes = List.copyOf(slotTimes);
        closures = Map.copyOf(closures);
        checklists = Map.copyOf(checklists);
        if (capacityPerSlot < 1 || capacityPerSlot > 50)
            throw new IllegalArgumentException("Capacity must be between 1 and 50 per slot.");
        if (slotTimes.isEmpty() || slotTimes.size() > 20 || slotTimes.stream().distinct().count() != slotTimes.size())
            throw new IllegalArgumentException("Choose 1–20 distinct appointment times.");
        if (slotTimes.stream().anyMatch(t -> t.isBefore(LocalTime.of(6, 0)) || t.isAfter(LocalTime.of(20, 0))))
            throw new IllegalArgumentException("Appointment times must be between 06:00 and 20:00.");
        for (int i = 1; i < slotTimes.size(); i++)
            if (!slotTimes.get(i).isAfter(slotTimes.get(i - 1)))
                throw new IllegalArgumentException("List appointment times in order.");
    }

    public OfficePolicy withSchedule(List<LocalTime> times, int capacity) {
        return new OfficePolicy(officeId, times, capacity, closures, checklists);
    }

    public OfficePolicy withClosure(LocalDate date, Closure closure) {
        Map<LocalDate, Closure> updated = new LinkedHashMap<>(closures);
        if (closure == null) updated.remove(date); else updated.put(date, closure);
        return new OfficePolicy(officeId, slotTimes, capacityPerSlot, updated, checklists);
    }

    public OfficePolicy withChecklist(String serviceId, Checklist checklist) {
        Map<String, Checklist> updated = new LinkedHashMap<>(checklists);
        updated.put(serviceId, checklist);
        return new OfficePolicy(officeId, slotTimes, capacityPerSlot, closures, updated);
    }
}
