package com.github.arrivedbog593.economycore.platform.entity;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.platform.menu.Counter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The outfits a clerk can wear: skins that ship with the mod.
 * <p>
 * Packaged rather than fetched by player name. A skin looked up by name is
 * downloaded by every client from Mojang's servers, which an offline-mode
 * server cannot rely on; a texture inside the jar is there for everybody.
 * <p>
 * Each belongs to one counter, so an exchange clerk dresses like a cashier,
 * a shop clerk like a shopkeeper and a banker in the bank's green and gold,
 * and none ever turns up in another's clothes. Adding one is a PNG, a line here and its name in the
 * language files; it joins its counter's draw and cycle on its own.
 * <p>
 * Each is a standard 64×64 player skin in
 * {@code assets/economycore/textures/entity/clerk/}, so anything a skin
 * editor produces can replace it. Saved by id, so reordering this list
 * changes nobody's clerk.
 */
public enum ClerkLook {

    SUIT("suit", false, Counter.EXCHANGE),
    VEST("vest", false, Counter.EXCHANGE),
    BLAZER("blazer", true, Counter.EXCHANGE),

    APRON("apron", false, Counter.SHOP),
    SMOCK("smock", true, Counter.SHOP),
    MERCHANT("merchant", false, Counter.SHOP),

    BANKER("banker", false, Counter.BANK),
    TELLER("teller", false, Counter.BANK),
    MANAGER("manager", true, Counter.BANK);

    private final String id;
    private final boolean slim;
    private final Counter counter;
    private final ResourceLocation texture;

    ClerkLook(String id, boolean slim, Counter counter) {
        this.id = id;
        this.slim = slim;
        this.counter = counter;
        this.texture = ResourceLocation.fromNamespaceAndPath(EconomyCore.MOD_ID,
                "textures/entity/clerk/" + id + ".png");
    }

    public String id() {
        return id;
    }

    /** Whether the skin is drawn for the three-pixel arms rather than the four. */
    public boolean slim() {
        return slim;
    }

    public ResourceLocation texture() {
        return texture;
    }

    public String translationKey() {
        return "economycore.clerk.look." + id;
    }

    /** Every outfit a clerk at this counter may wear, in declaration order. */
    public static List<ClerkLook> of(Counter counter) {
        List<ClerkLook> looks = new ArrayList<>();
        for (ClerkLook look : values()) {
            if (look.counter == counter) {
                looks.add(look);
            }
        }
        return looks;
    }

    /** One of this counter's outfits, at random. */
    public static ClerkLook random(Counter counter, RandomSource random) {
        List<ClerkLook> looks = of(counter);
        return looks.get(random.nextInt(looks.size()));
    }

    /**
     * The next of this counter's outfits, for cycling through them in place.
     * <p>
     * One from the other counter — given by a summon command, say — moves on
     * to this counter's first.
     */
    public ClerkLook next(Counter at) {
        List<ClerkLook> looks = of(at);
        return looks.get((looks.indexOf(this) + 1) % looks.size());
    }

    /** The look saved under this id, or the counter's first for anything unknown. */
    public static ClerkLook byId(String id, Counter counter) {
        for (ClerkLook look : values()) {
            if (look.id.equals(id)) {
                return look;
            }
        }
        return of(counter).getFirst();
    }

    /** By position, for the synced value; anything out of range is the first. */
    public static ClerkLook byOrdinal(int ordinal) {
        ClerkLook[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : SUIT;
    }
}
