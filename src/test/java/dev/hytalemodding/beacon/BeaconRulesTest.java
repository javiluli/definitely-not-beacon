package dev.hytalemodding.beacon;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BeaconRulesTest {
    @Test
    void completePyramidRequiresAll164BlocksAndStopsAtFour() {
        Set<String> checked = new HashSet<>();
        assertEquals(4, BeaconRules.pyramidLevel((x, depth, z) -> {
            checked.add(x + "," + depth + "," + z);
            return true;
        }));
        assertEquals(164, checked.size());
    }

    @Test
    void everyMissingBlockLimitsTheTierEvenUnderCompleteLowerLayers() {
        for (int depth = 1; depth <= 4; depth++) {
            for (int x = -depth; x <= depth; x++) for (int z = -depth; z <= depth; z++) {
                int missingDepth = depth, missingX = x, missingZ = z;
                assertEquals(depth - 1, BeaconRules.pyramidLevel((dx, d, dz) ->
                        d != missingDepth || dx != missingX || dz != missingZ));
            }
        }
    }

    @Test
    void onlyConsecutiveCompleteLayersCount() {
        for (int tier = 0; tier <= 4; tier++) {
            int layers = tier;
            assertEquals(tier, BeaconRules.pyramidLevel((x, depth, z) -> depth <= layers));
        }
    }

    @Test
    void rangeScalesFromSixtyToOneHundredFiftyAndIncludesItsSphericalEdge() {
        int[] ranges = {0, 60, 90, 120, 150};
        for (int tier = 1; tier <= 4; tier++) {
            int range = ranges[tier];
            assertTrue(BeaconRules.inRange(.5 + range, .5, .5, 0, 0, 0, tier));
            assertFalse(BeaconRules.inRange(.501 + range, .5, .5, 0, 0, 0, tier));
            assertFalse(BeaconRules.inRange(.5 + range, .5 + range, .5, 0, 0, 0, tier));
        }
        assertFalse(BeaconRules.inRange(.5, .5, .5, 0, 0, 0, 0));
        assertTrue(BeaconRules.inRange(-31.5, -31.5, -.5, -32, -32, -1, 1));
    }

    @Test
    void primaryUnlocksAndSecondaryChoicesAreValidatedAtPayment() {
        for (BeaconPower power : BeaconPower.values()) {
            for (int tier = 0; tier <= 4; tier++) {
                assertEquals(power != BeaconPower.REGENERATION && tier >= power.tier,
                        BeaconRules.validSelection(tier, power, "NONE"));
                assertEquals(power != BeaconPower.REGENERATION && tier == 4,
                        BeaconRules.validSelection(tier, power, "UPGRADE"));
                assertEquals(power != BeaconPower.REGENERATION && tier == 4,
                        BeaconRules.validSelection(tier, power, "REGENERATION"));
            }
        }
        assertFalse(BeaconRules.validSelection(4, null, "NONE"));
        assertFalse(BeaconRules.validSelection(4, BeaconPower.HASTE, "invented"));
        assertFalse(BeaconRules.validSelection(4, BeaconPower.HASTE, null));
    }

    @Test
    void downgradedBaseSuspendsLockedPowersAndResumesPaidChoicesOnRepair() {
        for (BeaconPower primary : BeaconPower.values()) {
            if (primary == BeaconPower.REGENERATION) continue;
            assertFalse(BeaconRules.validSelection(3, primary, "UPGRADE"), primary.name());
            assertEquals(1, BeaconRules.strength(3, primary.name(), "UPGRADE", primary), primary.name());
            assertEquals(2, BeaconRules.strength(4, primary.name(), "UPGRADE", primary), primary.name());
        }
        assertEquals(0, BeaconRules.strength(2, "STRENGTH", "UPGRADE", BeaconPower.STRENGTH));
        assertEquals(1, BeaconRules.strength(3, "STRENGTH", "UPGRADE", BeaconPower.STRENGTH));
        assertEquals(2, BeaconRules.strength(4, "STRENGTH", "UPGRADE", BeaconPower.STRENGTH));
        assertEquals(0, BeaconRules.strength(0, "HASTE", "UPGRADE", BeaconPower.HASTE));
        assertEquals(0, BeaconRules.strength(4, "SPEED", "NONE", BeaconPower.HASTE));
        assertEquals(0, BeaconRules.strength(4, "HASTE", "UPGRADE", BeaconPower.REGENERATION));
        assertEquals(1, BeaconRules.strength(4, "HASTE", "REGENERATION", BeaconPower.REGENERATION));
        assertEquals(0, BeaconRules.strength(3, "HASTE", "REGENERATION", BeaconPower.REGENERATION));
        assertEquals(0, BeaconRules.strength(4, "", "NONE", BeaconPower.HASTE));
    }

    @Test
    void unknownSecondaryIsRejectedAtPaymentButKeepsThePaidPrimary() {
        for (String secondary : new String[]{"future", "", null}) {
            assertFalse(BeaconRules.validSelection(4, BeaconPower.HASTE, secondary));
            assertEquals(1, BeaconRules.strength(4, "HASTE", secondary, BeaconPower.HASTE));
            assertEquals(0, BeaconRules.strength(4, "HASTE", secondary, BeaconPower.REGENERATION));
        }
    }

    @Test
    void regenerationKeepsItsLegacyIndependenceFromThePrimary() {
        for (String primary : new String[]{"future", "", "haste", null}) {
            assertEquals(0, BeaconRules.strength(4, primary, "REGENERATION", BeaconPower.HASTE));
            assertEquals(1, BeaconRules.strength(4, primary, "REGENERATION", BeaconPower.REGENERATION));
            assertEquals(0, BeaconRules.strength(3, primary, "REGENERATION", BeaconPower.REGENERATION));
        }
    }

    @Test
    void overlapPrefersIntensityBeforeDuration() {
        assertTrue(BeaconRules.shouldReplacePower(1, 17, 2, 11));
        assertFalse(BeaconRules.shouldReplacePower(2, 11, 1, 17));
    }

    @Test
    void overlapUsesLongestDurationOnlyAtEqualPositiveIntensity() {
        assertTrue(BeaconRules.shouldReplacePower(1, 11, 1, 15));
        assertFalse(BeaconRules.shouldReplacePower(1, 15, 1, 11));
        assertFalse(BeaconRules.shouldReplacePower(1, 15, 1, 15));
    }

    @Test
    void overlapIgnoresSourcesWithoutPower() {
        assertFalse(BeaconRules.shouldReplacePower(0, 0, 0, 17));
        assertFalse(BeaconRules.shouldReplacePower(1, 11, 0, 17));
        assertTrue(BeaconRules.shouldReplacePower(0, 0, 1, 11));
    }

    @Test
    void onlyIngotIdsAreAccepted() {
        assertTrue(BeaconRules.isIngot("Ingredient_Bar_Iron"));
        assertTrue(BeaconRules.isIngot("Ingredient_Bar_Cobalt"));
        assertFalse(BeaconRules.isIngot("Ore_Iron"));
        assertFalse(BeaconRules.isIngot("Iron_Block"));
        assertFalse(BeaconRules.isIngot(null));
    }

    @Test
    void hasteAddsMiningPowerAndAttackSpeedPerIntensity() {
        assertEquals(10f, BeaconRules.miningDamage(10f, 0));
        assertEquals(12f, BeaconRules.miningDamage(10f, 1), .00001f);
        assertEquals(14f, BeaconRules.miningDamage(10f, 2), .00001f);
        assertEquals(1f, BeaconRules.attackSpeed(0));
        assertEquals(1.4f, BeaconRules.attackSpeed(1));
        assertEquals(1.6f, BeaconRules.attackSpeed(2));
        assertEquals(8, hits(.125f, 0));
        assertEquals(7, hits(.125f, 1));
        assertEquals(6, hits(.125f, 2));
        assertEquals(16, hits(.063f, 0));
        assertEquals(14, hits(.063f, 1));
        assertEquals(2, hits(.5f, 1));
        assertTrue(BeaconRules.isPickaxe("Pickaxe"));
        assertFalse(BeaconRules.isPickaxe("Hatchet"));
        assertFalse(BeaconRules.isPickaxe(null));
    }

    @Test
    void balanceUsesExplicitHytaleBonusesAndResidualDurations() {
        int[] ranges = {0, 60, 90, 120, 150};
        for (int tier = 1; tier <= 4; tier++) {
            assertEquals(ranges[tier], BeaconPower.radius(tier));
            assertEquals(9 + 2 * tier, BeaconPower.durationSeconds(tier));
        }
        assertEquals(0, BeaconPower.durationSeconds(0));
        assertEquals(0, BeaconPower.radius(5));
        assertEquals(1.15f, BeaconPower.JUMP_BOOST.multiplier(1), .00001f);
        assertEquals(1.30f, BeaconPower.JUMP_BOOST.multiplier(2), .00001f);
        assertEquals(.40f, BeaconPower.SPEED.bonus(1));
        assertEquals(.60f, BeaconPower.SPEED.bonus(2));
        assertEquals(.40f, BeaconPower.STRENGTH.bonus(1));
        assertEquals(.80f, BeaconPower.STRENGTH.bonus(2));
        assertEquals(.05f, BeaconPower.RESISTANCE.bonus(1));
        assertEquals(.08f, BeaconPower.RESISTANCE.bonus(2));
        assertEquals(0f, BeaconPower.resistanceHealth(0));
        assertEquals(9f, BeaconPower.resistanceHealth(1));
        assertEquals(14f, BeaconPower.resistanceHealth(2));
        assertEquals(2f, BeaconPower.REGENERATION.bonus(1));
        assertEquals(1f, BeaconPower.REGENERATION_INTERVAL_SECONDS);
        assertThrows(IllegalArgumentException.class, () -> BeaconPower.REGENERATION.bonus(2));
        assertThrows(IllegalArgumentException.class, () -> BeaconPower.HASTE.bonus(-1));
        assertThrows(IllegalArgumentException.class, () -> BeaconPower.hasteAttackBonus(3));
        assertThrows(IllegalArgumentException.class, () -> BeaconPower.resistanceHealth(-1));
    }

    private int hits(float damage, int strength) {
        float health = 1f;
        int hits = 0;
        while (health > .000001f) { health -= BeaconRules.miningDamage(damage, strength); hits++; }
        return hits;
    }
}
