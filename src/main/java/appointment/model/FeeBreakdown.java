package appointment.model;

import java.util.ArrayList;
import java.util.List;

/** An itemised fee. Amounts are kept to two decimal places. */
public class FeeBreakdown {

    private final List<FeeLine> lines = new ArrayList<>();

    public FeeBreakdown add(String label, double amount) {
        double rounded = Math.round(amount * 100.0) / 100.0;
        if (rounded > 0) {
            lines.add(new FeeLine(label, rounded));
        }
        return this;
    }

    public FeeBreakdown addAll(List<FeeLine> more) {
        more.forEach(line -> add(line.label(), line.amount()));
        return this;
    }

    public List<FeeLine> getLines() {
        return List.copyOf(lines);
    }

    public double getTotal() {
        double sum = 0;
        for (FeeLine line : lines) sum += line.amount();
        return Math.round(sum * 100.0) / 100.0;
    }
}
