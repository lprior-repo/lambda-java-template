package software.amazonaws.example.ordervalidation;

import java.util.ArrayList;
import java.util.List;

/**
 * Result object for order validation operations.
 */
public class OrderValidationResult {
    private final List<String> errors = new ArrayList<>();

    public void addError(String error) {
        this.errors.add(error);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
}