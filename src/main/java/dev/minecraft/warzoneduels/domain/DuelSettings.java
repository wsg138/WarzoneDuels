package dev.minecraft.warzoneduels.domain;

public final class DuelSettings {
    public enum PlaceBreakMode {
        NONE,
        PLACE_ONLY,
        PLACE_BREAK
    }

    public enum PlaceOnlyMode {
        COBWEB_UTILS,
        ALL_BLOCKS
    }

    private PlaceBreakMode placeBreakMode = PlaceBreakMode.NONE;
    private PlaceOnlyMode placeOnlyMode = PlaceOnlyMode.COBWEB_UTILS;
    private String mapId = "flat_arena";
    private String mapDisplayName = "Flat Arena";
    private String mapDescription = "Classic flat colosseum floor.";
    private boolean mapSupportsPlaceOnly = true;
    private boolean mapSupportsBlockBreaking;
    private boolean mapSupportsProtectedExplosives = true;
    private String mapSchematicFile = "";
    private boolean mapPasteAir;
    private boolean allowCrystalsAnchors;
    private boolean allowExplosiveMinecarts;
    private boolean allowOtherExplosives;
    private boolean allowEnderPearls = true;
    private int enderPearlCooldownSeconds;
    private boolean allowWindCharges = true;
    private int windChargeCooldownSeconds;
    private boolean allowMaces = true;
    private boolean allowChorusFruit = true;
    private boolean allowSpears = true;
    private boolean allowElytras = true;
    private boolean allowEnderChests;
    private double wager;

    public String getMapId() {
        return mapId;
    }

    public void setMapId(String mapId) {
        this.mapId = mapId;
    }

    public String getMapDisplayName() {
        return mapDisplayName;
    }

    public void setMapDisplayName(String mapDisplayName) {
        this.mapDisplayName = mapDisplayName;
    }

    public String getMapDescription() {
        return mapDescription;
    }

    public void setMapDescription(String mapDescription) {
        this.mapDescription = mapDescription;
    }

    public boolean isMapSupportsBlockBreaking() {
        return mapSupportsBlockBreaking;
    }

    public void setMapSupportsBlockBreaking(boolean mapSupportsBlockBreaking) {
        this.mapSupportsBlockBreaking = mapSupportsBlockBreaking;
    }

    public boolean isMapSupportsPlaceOnly() {
        return mapSupportsPlaceOnly;
    }

    public void setMapSupportsPlaceOnly(boolean mapSupportsPlaceOnly) {
        this.mapSupportsPlaceOnly = mapSupportsPlaceOnly;
    }

    public boolean isMapSupportsProtectedExplosives() {
        return mapSupportsProtectedExplosives;
    }

    public void setMapSupportsProtectedExplosives(boolean mapSupportsProtectedExplosives) {
        this.mapSupportsProtectedExplosives = mapSupportsProtectedExplosives;
    }

    public String getMapSchematicFile() {
        return mapSchematicFile;
    }

    public void setMapSchematicFile(String mapSchematicFile) {
        this.mapSchematicFile = mapSchematicFile;
    }

    public boolean isMapPasteAir() {
        return mapPasteAir;
    }

    public void setMapPasteAir(boolean mapPasteAir) {
        this.mapPasteAir = mapPasteAir;
    }

    public PlaceBreakMode getPlaceBreakMode() {
        return placeBreakMode;
    }

    public void setPlaceBreakMode(PlaceBreakMode placeBreakMode) {
        this.placeBreakMode = placeBreakMode;
    }

    public PlaceOnlyMode getPlaceOnlyMode() {
        return placeOnlyMode;
    }

    public void setPlaceOnlyMode(PlaceOnlyMode placeOnlyMode) {
        this.placeOnlyMode = placeOnlyMode;
    }

    public boolean isAllowCrystalsAnchors() {
        return allowCrystalsAnchors;
    }

    public void setAllowCrystalsAnchors(boolean allowCrystalsAnchors) {
        this.allowCrystalsAnchors = allowCrystalsAnchors;
    }

    public boolean isAllowExplosiveMinecarts() {
        return allowExplosiveMinecarts;
    }

    public void setAllowExplosiveMinecarts(boolean allowExplosiveMinecarts) {
        this.allowExplosiveMinecarts = allowExplosiveMinecarts;
    }

    public boolean isAllowOtherExplosives() {
        return allowOtherExplosives;
    }

    public void setAllowOtherExplosives(boolean allowOtherExplosives) {
        this.allowOtherExplosives = allowOtherExplosives;
    }

    public boolean isAllowEnderPearls() {
        return allowEnderPearls;
    }

    public void setAllowEnderPearls(boolean allowEnderPearls) {
        this.allowEnderPearls = allowEnderPearls;
    }

    public int getEnderPearlCooldownSeconds() {
        return enderPearlCooldownSeconds;
    }

    public void setEnderPearlCooldownSeconds(int enderPearlCooldownSeconds) {
        this.enderPearlCooldownSeconds = Math.max(0, enderPearlCooldownSeconds);
    }

    public boolean isAllowWindCharges() {
        return allowWindCharges;
    }

    public void setAllowWindCharges(boolean allowWindCharges) {
        this.allowWindCharges = allowWindCharges;
    }

    public int getWindChargeCooldownSeconds() {
        return windChargeCooldownSeconds;
    }

    public void setWindChargeCooldownSeconds(int windChargeCooldownSeconds) {
        this.windChargeCooldownSeconds = Math.max(0, windChargeCooldownSeconds);
    }

    public boolean isAllowMaces() {
        return allowMaces;
    }

    public void setAllowMaces(boolean allowMaces) {
        this.allowMaces = allowMaces;
    }

    public boolean isAllowChorusFruit() {
        return allowChorusFruit;
    }

    public void setAllowChorusFruit(boolean allowChorusFruit) {
        this.allowChorusFruit = allowChorusFruit;
    }

    public boolean isAllowSpears() {
        return allowSpears;
    }

    public void setAllowSpears(boolean allowSpears) {
        this.allowSpears = allowSpears;
    }

    public boolean isAllowElytras() {
        return allowElytras;
    }

    public void setAllowElytras(boolean allowElytras) {
        this.allowElytras = allowElytras;
    }

    public boolean isAllowEnderChests() {
        return allowEnderChests;
    }

    public void setAllowEnderChests(boolean allowEnderChests) {
        this.allowEnderChests = allowEnderChests;
    }

    public double getWager() {
        return wager;
    }

    public void setWager(double wager) {
        this.wager = wager;
    }

    public String formatBlockRules() {
        return switch (placeBreakMode) {
            case NONE -> "No building or breaking";
            case PLACE_ONLY -> placeOnlyMode == PlaceOnlyMode.COBWEB_UTILS
                ? "Limited placement: utilities only"
                : "Limited placement: all placeable blocks";
            case PLACE_BREAK -> "Full placement and terrain breaking";
        };
    }

    public String formatMap() {
        String suffix = mapSupportsBlockBreaking
            ? "terrain duel ready"
            : mapSupportsPlaceOnly ? "protected terrain" : "restricted layout";
        return mapDisplayName + " (" + suffix + ")";
    }

    public String formatExplosives() {
        if (placeBreakMode == PlaceBreakMode.NONE) {
            return "Explosives are not part of this ruleset";
        }
        if (placeBreakMode == PlaceBreakMode.PLACE_ONLY && placeOnlyMode == PlaceOnlyMode.COBWEB_UTILS) {
            return "Explosives disabled in utilities-only placement";
        }
        if (!shouldShowExplosivesConfiguration()) {
            return "This map does not offer explosive rules in protected-terrain mode";
        }
        return "Crystals/Anchors: " + (allowCrystalsAnchors ? "Enabled" : "Disabled")
            + ", Minecarts: " + (allowExplosiveMinecarts ? "Enabled" : "Disabled")
            + ", Other: " + (allowOtherExplosives ? "Enabled" : "Disabled");
    }

    public String formatItemRules() {
        return "Pearls: " + onOff(allowEnderPearls) + formatCooldown(enderPearlCooldownSeconds)
            + ", Wind Charges: " + onOff(allowWindCharges) + formatCooldown(windChargeCooldownSeconds)
            + ", Maces: " + onOff(allowMaces)
            + ", Chorus Fruit: " + onOff(allowChorusFruit);
    }

    public String formatExtendedItemRules() {
        return formatItemRules()
            + ", Spears: " + onOff(allowSpears)
            + ", Elytras: " + onOff(allowElytras)
            + ", Ender Chests: " + onOff(allowEnderChests);
    }

    public boolean hasCustomAdvancementRules() {
        return placeBreakMode != PlaceBreakMode.NONE
            || !"flat_arena".equalsIgnoreCase(mapId)
            || allowCrystalsAnchors
            || allowExplosiveMinecarts
            || allowOtherExplosives
            || !allowEnderPearls
            || enderPearlCooldownSeconds > 0
            || !allowWindCharges
            || windChargeCooldownSeconds > 0
            || !allowMaces
            || !allowChorusFruit
            || !allowSpears
            || !allowElytras
            || allowEnderChests;
    }

    public boolean isRestrictedMobilityRuleset() {
        return !allowEnderPearls && !allowWindCharges;
    }

    public DuelSettings copy() {
        DuelSettings copy = new DuelSettings();
        copy.placeBreakMode = placeBreakMode;
        copy.placeOnlyMode = placeOnlyMode;
        copy.mapId = mapId;
        copy.mapDisplayName = mapDisplayName;
        copy.mapDescription = mapDescription;
        copy.mapSupportsPlaceOnly = mapSupportsPlaceOnly;
        copy.mapSupportsBlockBreaking = mapSupportsBlockBreaking;
        copy.mapSupportsProtectedExplosives = mapSupportsProtectedExplosives;
        copy.mapSchematicFile = mapSchematicFile;
        copy.mapPasteAir = mapPasteAir;
        copy.allowCrystalsAnchors = allowCrystalsAnchors;
        copy.allowExplosiveMinecarts = allowExplosiveMinecarts;
        copy.allowOtherExplosives = allowOtherExplosives;
        copy.allowEnderPearls = allowEnderPearls;
        copy.enderPearlCooldownSeconds = enderPearlCooldownSeconds;
        copy.allowWindCharges = allowWindCharges;
        copy.windChargeCooldownSeconds = windChargeCooldownSeconds;
        copy.allowMaces = allowMaces;
        copy.allowChorusFruit = allowChorusFruit;
        copy.allowSpears = allowSpears;
        copy.allowElytras = allowElytras;
        copy.allowEnderChests = allowEnderChests;
        copy.wager = wager;
        return copy;
    }

    private String onOff(boolean value) {
        return value ? "Enabled" : "Disabled";
    }

    private String formatCooldown(int seconds) {
        return seconds > 0 ? " (" + seconds + "s CD)" : "";
    }

    public boolean shouldShowExplosivesConfiguration() {
        if (placeBreakMode == PlaceBreakMode.PLACE_BREAK) {
            return mapSupportsBlockBreaking;
        }
        return placeBreakMode == PlaceBreakMode.PLACE_ONLY
            && placeOnlyMode == PlaceOnlyMode.ALL_BLOCKS
            && mapSupportsProtectedExplosives;
    }

    public void clearExplosiveRules() {
        allowCrystalsAnchors = false;
        allowExplosiveMinecarts = false;
        allowOtherExplosives = false;
    }
}
