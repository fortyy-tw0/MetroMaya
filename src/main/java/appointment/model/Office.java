package appointment.model;

import java.util.Set;

/** A branch where appointments are served. Each time slot can hold {@code capacityPerSlot} citizens. */
public class Office {

    private final String id;
    private final String name;
    private final String address;
    private final int capacityPerSlot;
    private final Set<String> serviceIds;

    public Office(String id, String name, String address, int capacityPerSlot, Set<String> serviceIds) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.capacityPerSlot = capacityPerSlot;
        this.serviceIds = Set.copyOf(serviceIds);
    }

    public boolean offers(String serviceId) {
        return serviceIds.contains(serviceId);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public int getCapacityPerSlot() { return capacityPerSlot; }
    public Set<String> getServiceIds() { return serviceIds; }
}
