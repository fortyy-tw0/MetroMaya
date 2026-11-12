package appointment.model;

import java.util.regex.Pattern;

/** The owner or representative who books the slot. Validates its own contact details. */
public class Applicant {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+\\-\\s]{7,18}$");

    private final String fullName;
    private final String email;
    private final String phone;

    public Applicant(String fullName, String email, String phone) {
        if (fullName == null || fullName.trim().length() < 2) {
            throw new IllegalArgumentException("Please enter the owner's full name.");
        }
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        if (phone == null || !PHONE.matcher(phone.trim()).matches()) {
            throw new IllegalArgumentException("Please enter a valid mobile number.");
        }
        this.fullName = fullName.trim();
        this.email = email.trim().toLowerCase();
        this.phone = phone.trim();
    }

    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
}
