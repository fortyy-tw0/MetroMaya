package appointment.model;

import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

/** The person booking an appointment. Validates its own data on construction. */
public class Citizen {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+\\-\\s]{7,18}$");

    private final String fullName;
    private final String email;
    private final String phone;
    private final LocalDate birthDate;

    public Citizen(String fullName, String email, String phone, LocalDate birthDate) {
        if (fullName == null || fullName.trim().length() < 2) {
            throw new IllegalArgumentException("Please enter your full name.");
        }
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        if (phone == null || !PHONE.matcher(phone.trim()).matches()) {
            throw new IllegalArgumentException("Please enter a valid phone number.");
        }
        if (birthDate == null || birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Please enter a valid birth date.");
        }
        this.fullName = fullName.trim();
        this.email = email.trim().toLowerCase();
        this.phone = phone.trim();
        this.birthDate = birthDate;
    }

    public int ageOn(LocalDate date) {
        return Period.between(birthDate, date).getYears();
    }

    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public LocalDate getBirthDate() { return birthDate; }
}
