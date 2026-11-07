package com.github.arrivedbog593.economycore.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankLevelsTest {

    private static BankLevels read(String... lines) {
        return BankLevels.parse(List.of(lines), new ArrayList<>()).orElseThrow();
    }

    @Test
    void readsEveryPart() {
        BankLevels levels = read(
                "starter; cap=50000000; tranches=10000000:2,15000000:1",
                "gold; cap=100_000_000; cost=5000000; items=minecraft:gold_block*1, minecraft:diamond; "
                        + "tranches=10000000:2,20000000:1");
        BankLevels.Level gold = levels.level(1);
        assertEquals("gold", gold.name());
        assertEquals(100_000_000L, gold.cap());
        assertEquals(5_000_000L, gold.credits());
        assertEquals(List.of(new BankLevels.ItemCost("minecraft:gold_block", 1),
                new BankLevels.ItemCost("minecraft:diamond", 1)), gold.items());
        assertEquals(300_000, gold.tranches().maxInterest());
    }

    /**
     * The config's defaults, SkyBlock's seven. Kept in step with
     * {@code EconomyConfig} by hand: the config cannot be built without the
     * game. Palatial's 0.001% once failed to read, and took every level with it.
     */
    @Test
    void skyblocksSevenReadWithTheirMostInterest() {
        BankLevels levels = read(
                "starter; cap=50000000; tranches=10000000:2,15000000:1",
                "gold; cap=100000000; cost=5000000; items=minecraft:gold_block*1;"
                        + " tranches=10000000:2,20000000:1",
                "deluxe; cap=250000000; cost=10000000; items=minecraft:gold_block*5;"
                        + " tranches=10000000:2,20000000:1,30000000:0.5",
                "super_deluxe; cap=500000000; cost=25000000; items=minecraft:gold_block*20;"
                        + " tranches=10000000:2,20000000:1,30000000:0.5,50000000:0.2",
                "premier; cap=1000000000; cost=50000000; items=minecraft:gold_block*50;"
                        + " tranches=10000000:2,20000000:1,30000000:0.5,50000000:0.2,160000000:0.1",
                "luxurious; cap=6000000000; cost=100000000; items=minecraft:gold_block*100;"
                        + " tranches=10000000:2,20000000:1,30000000:0.5,50000000:0.2,160000000:0.1,"
                        + "5160000000:0.01",
                "palatial; cap=60000000000; cost=200000000; items=minecraft:gold_block*200;"
                        + " tranches=10000000:2,20000000:1,30000000:0.5,50000000:0.2,160000000:0.1,"
                        + "5160000000:0.01,55160000000:0.001");
        // The wiki's "Max Interest" column.
        long[] most = {250_000, 300_000, 350_000, 390_000, 500_000, 1_000_000, 1_500_000};
        for (int i = 0; i < most.length; i++) {
            assertEquals(most[i], levels.level(i).tranches().maxInterest(), levels.level(i).name());
        }
    }

    @Test
    void aLevelWritesTheLineItIsReadFrom() {
        String first = "starter; cap=1000";
        String second = "gold; cap=5000; cost=900; items=minecraft:gold_block*5,minecraft:diamond*1;"
                + " tranches=1000:2,2000:0.001";
        BankLevels levels = read(first, second);
        assertEquals(first, levels.level(0).toLine());
        assertEquals(second, levels.level(1).toLine());
    }

    @Test
    void leftOutTranchesMeanTheServersOwn() {
        assertNull(read("starter; cap=1000").level(0).tranches());
    }

    @Test
    void theFirstLevelIsFree() {
        BankLevels.Level first = read("starter; cap=1000; cost=50; items=minecraft:dirt*3").level(0);
        assertEquals(0, first.credits());
        assertTrue(first.items().isEmpty());
    }

    @Test
    void aCostAboveWhatTheLevelBeforeHoldsIsBroughtDown() {
        List<String> warnings = new ArrayList<>();
        BankLevels levels = BankLevels.parse(List.of("starter; cap=1000", "gold; cap=5000; cost=2000"), warnings)
                .orElseThrow();
        assertEquals(1000, levels.level(1).credits());
        assertEquals(1, warnings.size());
    }

    @Test
    void nextAndLevelStayInRange() {
        BankLevels levels = read("starter; cap=1000", "gold; cap=5000");
        assertEquals("gold", levels.next(0).orElseThrow().name());
        assertTrue(levels.next(1).isEmpty());
        assertEquals("gold", levels.level(9).name(), "a list shortened since keeps the top one");
        assertEquals(1, levels.indexOf("gold"));
        assertEquals(-1, levels.indexOf("palatial"));
    }

    @Test
    void aPlayerKeepsTheirLevelByNameWhateverMovesAroundIt() {
        BankLevels before = read("starter; cap=1000", "gold; cap=5000");
        BankLevels.Reached gold = before.reached(1);
        BankLevels after = read("starter; cap=1000", "silver; cap=3000", "gold; cap=5000", "emerald; cap=9000");
        assertEquals(2, after.placeOf(gold), "a level inserted below does not move them");
        assertEquals(0, after.placeOf(null), "nobody upgraded starts at the first");
    }

    @Test
    void aPlayerWhoseLevelIsGoneDropsToTheHighestNoBigger() {
        BankLevels levels = read("starter; cap=1000", "silver; cap=3000", "emerald; cap=9000");
        assertEquals(1, levels.placeOf(new BankLevels.Reached("gold", 5000)));
        assertEquals(0, levels.placeOf(new BankLevels.Reached("tiny", 10)));
    }

    @Test
    void anythingUnreadableReadsAsNothing() {
        List<String> warnings = new ArrayList<>();
        assertTrue(BankLevels.parse(List.of("starter; cap=lots"), warnings).isEmpty());
        assertTrue(BankLevels.parse(List.of("starter; cap=1000; colour=gold"), warnings).isEmpty());
        assertTrue(BankLevels.parse(List.of("Starter; cap=1000"), warnings).isEmpty());
        assertTrue(BankLevels.parse(List.of("starter; cap=1000", "starter; cap=2000"), warnings).isEmpty());
        assertTrue(BankLevels.parse(List.of("starter; cap=2000", "gold; cap=1000"), warnings).isEmpty());
        assertTrue(BankLevels.parse(List.of("starter"), warnings).isEmpty(), "a level must say what it holds");
        assertTrue(BankLevels.parse(List.of("starter; cap=1000; tranches=10:x"), warnings).isEmpty());
    }
}
