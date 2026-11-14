package appointment.model;

/** A staff identity. District heads and staff are restricted to one office; admins have no office. */
public record StaffAccount(String username, String passwordHash, Role role, String officeId, boolean active) {
    public enum Role { STAFF, DISTRICT_HEAD, ADMIN, TREASURER }

    public StaffAccount {
        if (username == null || !username.matches("[a-z0-9][a-z0-9._-]{2,39}"))
            throw new IllegalArgumentException("Username must be 3–40 lowercase letters, numbers, dots, dashes or underscores.");
        if (role == null || (role != Role.ADMIN && (officeId == null || officeId.isBlank())))
            throw new IllegalArgumentException("Staff and district heads need an office.");
        if (role == Role.ADMIN && officeId != null)
            throw new IllegalArgumentException("Administrators are not assigned to one office.");
    }
}
