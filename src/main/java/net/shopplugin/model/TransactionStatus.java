package net.shopplugin.model;

/**
 * All possible outcomes of a shop transaction (buy or sell).
 * Every transaction path must resolve to exactly one of these statuses;
 * no transaction may silently fail or leave an ambiguous state.
 */
public enum TransactionStatus {
    SUCCESS(true),
    INVALID_QUANTITY(false),
    ITEM_NOT_FOUND(false),
    BUY_DISABLED(false),
    SELL_DISABLED(false),
    INSUFFICIENT_FUNDS(false),
    INSUFFICIENT_ITEMS(false),
    INVENTORY_FULL(false),
    OUT_OF_STOCK(false),
    PURCHASE_LIMIT_REACHED(false),
    ECONOMY_UNAVAILABLE(false),
    ECONOMY_FAILURE(false),
    TRANSACTION_CONFLICT(false),
    CONFIGURATION_ERROR(false),
    PLAYER_OFFLINE(false),
    INTERNAL_ERROR(false),
    CANCELLED_BY_EVENT(false);

    private final boolean success;

    TransactionStatus(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }
}
