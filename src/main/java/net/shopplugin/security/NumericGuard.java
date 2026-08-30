package net.shopplugin.security;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Centralized numeric-safety checks. All quantity and currency arithmetic
 * in transaction code should route through here rather than doing raw
 * int/double math inline, so overflow and precision bugs have one place
 * to be fixed instead of many.
 */
public final class NumericGuard {

    private NumericGuard() {
    }

    /** Absolute hard ceiling on any single transaction's item quantity, regardless of config. */
    public static final long ABSOLUTE_MAX_QUANTITY = 1_000_000L;

    /**
     * Validates a requested quantity against item and server-wide limits.
     * Returns true only if the quantity is strictly positive, finite,
     * within the per-item configured max, and within the absolute ceiling.
     */
    public static boolean isValidQuantity(long quantity, int perItemMax) {
        if (quantity <= 0) {
            return false;
        }
        if (quantity > ABSOLUTE_MAX_QUANTITY) {
            return false;
        }
        if (perItemMax > 0 && quantity > perItemMax) {
            return false;
        }
        return true;
    }

    /**
     * Safely multiplies a per-unit price by a quantity using BigDecimal,
     * never floating point, and rounds to the economy's fractional digits.
     * Throws ArithmeticException on overflow rather than silently wrapping.
     */
    public static BigDecimal safeMultiply(BigDecimal unitPrice, long quantity, int fractionalDigits) {
        if (unitPrice == null) {
            throw new IllegalArgumentException("unitPrice must not be null");
        }
        if (quantity <= 0 || quantity > ABSOLUTE_MAX_QUANTITY) {
            throw new IllegalArgumentException("quantity out of safe bounds: " + quantity);
        }
        BigDecimal result = unitPrice.multiply(BigDecimal.valueOf(quantity));
        // Guard against unreasonable totals that would indicate a config or logic error.
        if (result.compareTo(BigDecimal.valueOf(1_000_000_000L)) > 0) {
            throw new ArithmeticException("Computed transaction total exceeds safety ceiling: " + result);
        }
        return result.setScale(Math.max(fractionalDigits, 0), RoundingMode.HALF_UP);
    }

    /**
     * Parses a player-supplied quantity string (e.g. from a chat prompt or
     * command argument) safely, rejecting anything that isn't a clean
     * positive integer. Returns -1 on any parse failure or invalid input.
     */
    public static long parseQuantitySafely(String input) {
        if (input == null) {
            return -1;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty() || trimmed.length() > 12) {
            return -1;
        }
        if (!trimmed.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        try {
            long value = Long.parseLong(trimmed);
            if (value <= 0 || value > ABSOLUTE_MAX_QUANTITY) {
                return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
