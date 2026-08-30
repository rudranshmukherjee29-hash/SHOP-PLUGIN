package net.shopplugin.model;

import java.math.BigDecimal;

/**
 * Immutable outcome of a single transaction attempt. Every code path that
 * performs a buy/sell operation must return one of these, so callers can
 * never mistake a partial or failed operation for a success.
 */
public final class TransactionResult {

    private final TransactionStatus status;
    private final String itemId;
    private final long quantity;
    private final BigDecimal amount;
    private final String message;

    private TransactionResult(TransactionStatus status, String itemId, long quantity,
                               BigDecimal amount, String message) {
        this.status = status;
        this.itemId = itemId;
        this.quantity = quantity;
        this.amount = amount;
        this.message = message;
    }

    public static TransactionResult success(String itemId, long quantity, BigDecimal amount) {
        return new TransactionResult(TransactionStatus.SUCCESS, itemId, quantity, amount, null);
    }

    public static TransactionResult failure(TransactionStatus status, String itemId, String message) {
        if (status == TransactionStatus.SUCCESS) {
            throw new IllegalArgumentException("failure() cannot be called with SUCCESS status");
        }
        return new TransactionResult(status, itemId, 0L, BigDecimal.ZERO, message);
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return status.isSuccess();
    }

    public String getItemId() {
        return itemId;
    }

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMessage() {
        return message;
    }
}
