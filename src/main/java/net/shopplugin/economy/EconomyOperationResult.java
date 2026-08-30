package net.shopplugin.economy;

public final class EconomyOperationResult {

    private final boolean success;
    private final String errorMessage;

    private EconomyOperationResult(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static EconomyOperationResult ok() {
        return new EconomyOperationResult(true, null);
    }

    public static EconomyOperationResult failed(String reason) {
        return new EconomyOperationResult(false, reason);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
