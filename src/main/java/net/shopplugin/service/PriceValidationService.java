package net.shopplugin.service;

import net.shopplugin.model.ConversionRelation;
import net.shopplugin.model.ShopItem;
import net.shopplugin.repository.ShopRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates the configured price table for common misconfigurations and
 * arbitrage loops, per section 11 (/shopadmin validateprices) and section 4
 * (economy safety) of the design spec. This runs at startup (logging
 * warnings) and on-demand via command (reporting to the invoking admin).
 *
 * It is intentionally conservative: it flags anything that LOOKS like it
 * could be a loop so a human reviews it, rather than trying to be fully
 * automatic about vanilla recipe knowledge.
 */
public final class PriceValidationService {

    private final ShopRepository repository;
    private final List<ConversionRelation> knownConversions;

    /** Safety margin: sell(output) must be at least this much below the raw input cost. */
    private static final BigDecimal MIN_LOOP_MARGIN = new BigDecimal("0.90");

    public PriceValidationService(ShopRepository repository, List<ConversionRelation> knownConversions) {
        this.repository = repository;
        this.knownConversions = knownConversions;
    }

    public List<String> validate() {
        List<String> warnings = new ArrayList<>();

        for (ShopItem item : repository.getAllItems()) {
            validateBasicSanity(item, warnings);
        }

        for (ConversionRelation relation : knownConversions) {
            validateConversion(relation, warnings);
        }

        return warnings;
    }

    private void validateBasicSanity(ShopItem item, List<String> warnings) {
        if (item.getBuyPrice() != null && item.getBuyPrice().compareTo(BigDecimal.ZERO) < 0) {
            warnings.add("[" + item.getId() + "] Negative buy price configured.");
        }
        if (item.getSellPrice() != null && item.getSellPrice().compareTo(BigDecimal.ZERO) < 0) {
            warnings.add("[" + item.getId() + "] Negative sell price configured.");
        }
        if (item.isBuyable() && item.isSellable()
                && item.getSellPrice().compareTo(item.getBuyPrice()) >= 0) {
            warnings.add("[" + item.getId() + "] Sell price (" + item.getSellPrice()
                    + ") is not lower than buy price (" + item.getBuyPrice()
                    + "). This allows direct buy/sell profit.");
        }
        if (item.isSellable() && item.getSellPrice().compareTo(BigDecimal.ZERO) == 0) {
            warnings.add("[" + item.getId() + "] Selling is enabled but sell price is zero.");
        }
    }

    private void validateConversion(ConversionRelation relation, List<String> warnings) {
        Optional<ShopItem> inputOpt = repository.getItem(relation.getInputItemId());
        Optional<ShopItem> outputOpt = repository.getItem(relation.getOutputItemId());
        if (inputOpt.isEmpty() || outputOpt.isEmpty()) {
            return; // one side not in the curated shop list; no loop possible through the shop
        }
        ShopItem input = inputOpt.get();
        ShopItem output = outputOpt.get();

        // Direction 1: buy inputs, convert, sell output.
        if (input.isBuyable() && output.isSellable()) {
            BigDecimal inputCost = input.getBuyPrice()
                    .multiply(BigDecimal.valueOf(relation.getInputAmount()));
            BigDecimal outputRevenue = output.getSellPrice()
                    .multiply(BigDecimal.valueOf(relation.getOutputAmount()));
            if (outputRevenue.compareTo(inputCost.multiply(MIN_LOOP_MARGIN)) >= 0) {
                warnings.add(String.format(
                        "[%s -> %s] Possible profit loop (%s): buying %d x %s (%s) and converting to %d x %s sells for %s.",
                        input.getId(), output.getId(), relation.getType(),
                        relation.getInputAmount(), input.getId(), inputCost,
                        relation.getOutputAmount(), output.getId(), outputRevenue));
            }
        }

        // Direction 2: buy output, break down (where mechanically reversible), sell inputs.
        if (relation.getType() == ConversionRelation.Type.DECOMPRESSION
                || relation.getType() == ConversionRelation.Type.COMPRESSION) {
            if (output.isBuyable() && input.isSellable()) {
                BigDecimal outputCost = output.getBuyPrice();
                BigDecimal inputRevenue = input.getSellPrice()
                        .multiply(BigDecimal.valueOf(relation.getInputAmount()));
                if (inputRevenue.compareTo(outputCost.multiply(MIN_LOOP_MARGIN)) >= 0) {
                    warnings.add(String.format(
                            "[%s -> %s] Possible reverse profit loop: buying 1 x %s (%s) and breaking into %d x %s sells for %s.",
                            output.getId(), input.getId(), output.getId(), outputCost,
                            relation.getInputAmount(), input.getId(), inputRevenue));
                }
            }
        }
    }
}
