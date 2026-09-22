package dev.minecraft.warzoneduels.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DuelSettingsTest {
    @Test
    void defaultsAreConservativeForTerrainAndExplosives() {
        DuelSettings settings = new DuelSettings();

        assertEquals(DuelSettings.PlaceBreakMode.NONE, settings.getPlaceBreakMode());
        assertEquals(DuelSettings.PlaceOnlyMode.COBWEB_UTILS, settings.getPlaceOnlyMode());
        assertFalse(settings.isAllowCrystalsAnchors());
        assertFalse(settings.isAllowExplosiveMinecarts());
        assertFalse(settings.isAllowOtherExplosives());
        assertTrue(settings.isAllowEnderPearls());
        assertTrue(settings.isAllowWindCharges());
        assertTrue(settings.isAllowMaces());
        assertTrue(settings.isAllowChorusFruit());
    }

    @Test
    void cooldownsAreClampedAtZero() {
        DuelSettings settings = new DuelSettings();

        settings.setEnderPearlCooldownSeconds(-10);
        settings.setWindChargeCooldownSeconds(-4);

        assertEquals(0, settings.getEnderPearlCooldownSeconds());
        assertEquals(0, settings.getWindChargeCooldownSeconds());
    }

    @Test
    void explosivesAreShownOnlyForCompatibleMapAndBuildModes() {
        DuelSettings settings = new DuelSettings();

        assertFalse(settings.shouldShowExplosivesConfiguration());

        settings.setPlaceBreakMode(DuelSettings.PlaceBreakMode.PLACE_BREAK);
        settings.setMapSupportsBlockBreaking(false);
        assertFalse(settings.shouldShowExplosivesConfiguration());
        settings.setMapSupportsBlockBreaking(true);
        assertTrue(settings.shouldShowExplosivesConfiguration());

        settings.setPlaceBreakMode(DuelSettings.PlaceBreakMode.PLACE_ONLY);
        settings.setPlaceOnlyMode(DuelSettings.PlaceOnlyMode.COBWEB_UTILS);
        assertFalse(settings.shouldShowExplosivesConfiguration());
        settings.setPlaceOnlyMode(DuelSettings.PlaceOnlyMode.ALL_BLOCKS);
        settings.setMapSupportsProtectedExplosives(true);
        assertTrue(settings.shouldShowExplosivesConfiguration());
        settings.setMapSupportsProtectedExplosives(false);
        assertFalse(settings.shouldShowExplosivesConfiguration());
    }

    @Test
    void clearingExplosiveRulesDisablesEveryExplosiveCategory() {
        DuelSettings settings = new DuelSettings();
        settings.setAllowCrystalsAnchors(true);
        settings.setAllowExplosiveMinecarts(true);
        settings.setAllowOtherExplosives(true);

        settings.clearExplosiveRules();

        assertFalse(settings.isAllowCrystalsAnchors());
        assertFalse(settings.isAllowExplosiveMinecarts());
        assertFalse(settings.isAllowOtherExplosives());
    }

    @Test
    void copyIsIndependentAndPreservesConfiguredValues() {
        DuelSettings original = new DuelSettings();
        original.setMapId("crystal_arena");
        original.setMapDisplayName("Crystal Arena");
        original.setMapDescription("Explosive test map");
        original.setMapSupportsBlockBreaking(true);
        original.setMapSupportsProtectedExplosives(false);
        original.setMapSchematicFile("crystal.schem");
        original.setMapPasteAir(true);
        original.setPlaceBreakMode(DuelSettings.PlaceBreakMode.PLACE_BREAK);
        original.setPlaceOnlyMode(DuelSettings.PlaceOnlyMode.ALL_BLOCKS);
        original.setAllowCrystalsAnchors(true);
        original.setAllowExplosiveMinecarts(true);
        original.setAllowOtherExplosives(true);
        original.setAllowEnderPearls(false);
        original.setEnderPearlCooldownSeconds(12);
        original.setAllowWindCharges(false);
        original.setWindChargeCooldownSeconds(8);
        original.setAllowMaces(false);
        original.setAllowChorusFruit(false);
        original.setAllowSpears(false);
        original.setAllowElytras(false);
        original.setAllowEnderChests(true);
        original.setWager(250.5);

        DuelSettings copy = original.copy();

        assertNotSame(original, copy);
        assertEquals(original.getMapId(), copy.getMapId());
        assertEquals(original.getMapDisplayName(), copy.getMapDisplayName());
        assertEquals(original.getMapDescription(), copy.getMapDescription());
        assertEquals(original.getPlaceBreakMode(), copy.getPlaceBreakMode());
        assertEquals(original.getPlaceOnlyMode(), copy.getPlaceOnlyMode());
        assertEquals(original.getEnderPearlCooldownSeconds(), copy.getEnderPearlCooldownSeconds());
        assertEquals(original.getWindChargeCooldownSeconds(), copy.getWindChargeCooldownSeconds());
        assertEquals(original.getWager(), copy.getWager());
        assertEquals(original.formatExtendedItemRules(), copy.formatExtendedItemRules());

        copy.setMapId("changed");
        copy.setWager(1.0);
        assertEquals("crystal_arena", original.getMapId());
        assertEquals(250.5, original.getWager());
    }

    @Test
    void humanReadableRuleSummariesFollowConfiguration() {
        DuelSettings settings = new DuelSettings();
        assertEquals("No building or breaking", settings.formatBlockRules());
        assertEquals("Flat Arena (protected terrain)", settings.formatMap());
        assertEquals("Explosives are not part of this ruleset", settings.formatExplosives());

        settings.setPlaceBreakMode(DuelSettings.PlaceBreakMode.PLACE_ONLY);
        assertEquals("Limited placement: utilities only", settings.formatBlockRules());
        assertEquals("Explosives disabled in utilities-only placement", settings.formatExplosives());

        settings.setPlaceOnlyMode(DuelSettings.PlaceOnlyMode.ALL_BLOCKS);
        settings.setAllowCrystalsAnchors(true);
        settings.setAllowExplosiveMinecarts(false);
        settings.setAllowOtherExplosives(true);
        assertEquals("Limited placement: all placeable blocks", settings.formatBlockRules());
        assertEquals("Crystals/Anchors: Enabled, Minecarts: Disabled, Other: Enabled", settings.formatExplosives());

        settings.setEnderPearlCooldownSeconds(5);
        settings.setWindChargeCooldownSeconds(7);
        assertTrue(settings.formatExtendedItemRules().contains("Pearls: Enabled (5s CD)"));
        assertTrue(settings.formatExtendedItemRules().contains("Wind Charges: Enabled (7s CD)"));
        assertTrue(settings.formatExtendedItemRules().contains("Spears: Enabled"));
        assertTrue(settings.formatExtendedItemRules().contains("Elytras: Enabled"));
        assertTrue(settings.formatExtendedItemRules().contains("Ender Chests: Disabled"));
    }
}
