package datrat.simplebridge.bridge;

import java.util.List;

public record ValidationResult(boolean valid, List<String> errors) {
    public static ValidationResult success() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult invalid(List<String> errors) {
        return new ValidationResult(false, List.copyOf(errors));
    }
}
