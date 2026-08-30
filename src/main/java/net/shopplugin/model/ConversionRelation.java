package net.shopplugin.model;

/**
 * Describes a known vanilla conversion between two shop items, e.g.
 * "9 iron ingots compress into 1 iron block" or "1 raw iron smelts into 1 iron ingot".
 * These are hand-curated (not derived from the game's recipe book at runtime)
 * because only a small, well-understood set of conversions actually matter
 * for economy balance, and hand-curation avoids false positives from
 * unrelated recipes that happen to share ingredients.
 */
public final class ConversionRelation {

    public enum Type {
        SMELTING,
        COMPRESSION,   // e.g. 9 ingots -> 1 block
        DECOMPRESSION, // e.g. 1 block -> 9 ingots
        CRAFTING       // general N inputs -> 1 output
    }

    private final String inputItemId;
    private final int inputAmount;
    private final String outputItemId;
    private final int outputAmount;
    private final Type type;

    public ConversionRelation(String inputItemId, int inputAmount, String outputItemId, int outputAmount, Type type) {
        this.inputItemId = inputItemId;
        this.inputAmount = inputAmount;
        this.outputItemId = outputItemId;
        this.outputAmount = outputAmount;
        this.type = type;
    }

    public String getInputItemId() {
        return inputItemId;
    }

    public int getInputAmount() {
        return inputAmount;
    }

    public String getOutputItemId() {
        return outputItemId;
    }

    public int getOutputAmount() {
        return outputAmount;
    }

    public Type getType() {
        return type;
    }
}
