package appointment.service;

/**
 * Someone who proved who they are before booking.
 *
 * @param id       stable ID used to cap their bookings: "egov:" + eGov SSO's uniqid, or "id:" + a hash of the scanned ID
 * @param fullName the name to book under, taken from eGovPH or the ID
 * @param email    from eGovPH when shared, else null
 * @param mobile   09XXXXXXXXX from eGovPH when shared, else null
 * @param method   "eGovPH" or the scanned document's type, e.g. "Philippine Driver's License"
 * @param address  the address on the ID or eGovPH profile, to prefill the form (never stored), or null
 */
public record VerifiedPerson(String id, String fullName, String email, String mobile, String method, Address address) {

    /** Each part may be null. {@code line1} is the unit/house number, street and subdivision only. */
    public record Address(String line1, String barangay, String city, String province, String zip) {

        /** Null when nothing useful was read. */
        static Address of(String line1, String barangay, String city, String province, String zip) {
            String z = zip == null ? null : zip.replaceAll("\\D", "");
            Address a = new Address(line1, barangay, city, province, z != null && z.length() == 4 ? z : null);
            return line1 == null && barangay == null && city == null ? null : a;
        }
    }
}
