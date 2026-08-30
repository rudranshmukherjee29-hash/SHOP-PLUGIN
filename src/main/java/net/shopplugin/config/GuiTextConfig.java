package net.shopplugin.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Typed view over gui.yml. Kept deliberately small: titles, a handful of
 * lore line templates, decorative slot list, and button slot positions.
 * Loaded once per reload rather than re-read per GUI open.
 */
public final class GuiTextConfig {

    private final String mainMenuTitle;
    private final String categoryTitleTemplate; // {category}
    private final String searchTitle;
    private final List<Integer> decorativeSlots;
    private final String decorativeMaterialName;
    private final int previousPageSlot;
    private final int nextPageSlot;
    private final int backButtonSlot;
    private final int closeButtonSlot;
    private final int searchButtonSlot;
    private final int itemsPerPage;
    private final boolean soundsEnabled;
    private final String purchaseSound;
    private final String sellSound;
    private final String errorSound;

    public GuiTextConfig(FileConfiguration config) {
        this.mainMenuTitle = config.getString("titles.main-menu", "<gradient:gold:yellow><bold>Server Shop</bold></gradient>");
        this.categoryTitleTemplate = config.getString("titles.category", "<gold>{category}</gold>");
        this.searchTitle = config.getString("titles.search", "<gold>Search Results</gold>");
        this.decorativeSlots = config.getIntegerList("layout.decorative-slots");
        this.decorativeMaterialName = config.getString("layout.decorative-material", "GRAY_STAINED_GLASS_PANE");
        this.previousPageSlot = config.getInt("layout.previous-page-slot", 45);
        this.nextPageSlot = config.getInt("layout.next-page-slot", 53);
        this.backButtonSlot = config.getInt("layout.back-button-slot", 49);
        this.closeButtonSlot = config.getInt("layout.close-button-slot", 48);
        this.searchButtonSlot = config.getInt("layout.search-button-slot", 50);
        this.itemsPerPage = config.getInt("layout.items-per-page", 28);
        this.soundsEnabled = config.getBoolean("sounds.enabled", true);
        this.purchaseSound = config.getString("sounds.purchase", "ENTITY_EXPERIENCE_ORB_PICKUP");
        this.sellSound = config.getString("sounds.sell", "ENTITY_VILLAGER_YES");
        this.errorSound = config.getString("sounds.error", "ENTITY_VILLAGER_NO");
    }

    public String getMainMenuTitle() {
        return mainMenuTitle;
    }

    public String getCategoryTitleTemplate() {
        return categoryTitleTemplate;
    }

    public String getSearchTitle() {
        return searchTitle;
    }

    public List<Integer> getDecorativeSlots() {
        return decorativeSlots;
    }

    public String getDecorativeMaterialName() {
        return decorativeMaterialName;
    }

    public int getPreviousPageSlot() {
        return previousPageSlot;
    }

    public int getNextPageSlot() {
        return nextPageSlot;
    }

    public int getBackButtonSlot() {
        return backButtonSlot;
    }

    public int getCloseButtonSlot() {
        return closeButtonSlot;
    }

    public int getSearchButtonSlot() {
        return searchButtonSlot;
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    public boolean isSoundsEnabled() {
        return soundsEnabled;
    }

    public String getPurchaseSound() {
        return purchaseSound;
    }

    public String getSellSound() {
        return sellSound;
    }

    public String getErrorSound() {
        return errorSound;
    }
}
