package appointment.model;

import java.util.Set;

/**
 * A BPLO desk or district team. Each office offers some services and serves some business sizes.
 * Each time slot holds {@code capacityPerSlot} bookings.
 */
public class Office {

    private final String id;
    private final String name;
    private final String address;
    private final int capacityPerSlot;
    private final Set<String> serviceIds;
    private final Set<BusinessSize> sizesServed;

    public Office(String id, String name, String address, int capacityPerSlot,
                  Set<String> serviceIds, Set<BusinessSize> sizesServed) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.capacityPerSlot = capacityPerSlot;
        this.serviceIds = Set.copyOf(serviceIds);
        this.sizesServed = Set.copyOf(sizesServed);
    }

    public boolean offers(String serviceId) {
        return serviceIds.contains(serviceId);
    }

    public boolean serves(BusinessSize size) {
        return sizesServed.contains(size);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public int getCapacityPerSlot() { return capacityPerSlot; }
    public Set<String> getServiceIds() { return serviceIds; }
    public Set<BusinessSize> getSizesServed() { return sizesServed; }
}
