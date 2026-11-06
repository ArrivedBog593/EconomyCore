package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.platform.EconomyClientConfig;
import com.github.arrivedbog593.economycore.platform.UiTheme;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Optional;

/**
 * Every color the mod's windows draw with, light or dark.
 * <p>
 * Light is vanilla's gray, so the shop and the exchange sit beside a chest
 * without looking borrowed from another game. Dark is for players who have
 * darkened the rest of Minecraft with a resource pack, beside which a gray
 * window glares. Which one is drawn is the player's choice in
 * {@code economycore-client.toml}; {@link UiTheme#AUTO}, the default, looks at
 * the texture vanilla draws its own chests with, as the resource packs in use
 * have left it, and goes dark if they made it dark.
 * <p>
 * Colored buttons are the same in both: white on green, amber, red or gray
 * reads on any background.
 *
 * @param panel       the window's body, in vanilla's frame
 * @param bevelLight  the frame's lit edge
 * @param bevelDark   the frame's shaded edge
 * @param slotFill    a sunken well or slot
 * @param slotDark    a well's shaded edge
 * @param label       text on the panel
 * @param positive    a balance, credits coming in
 * @param negative    a shortfall, credits going out
 */
public record Palette(int panel, int bevelLight, int bevelDark, int slotFill, int slotDark,
                      int label, int positive, int negative) {

    public static final Palette LIGHT = new Palette(
            0xFFC6C6C6, 0xFFFFFFFF, 0xFF555555, 0xFF8B8B8B, 0xFF373737,
            0xFF404040, 0xFF2E7D32, 0xFF9C1C1C);

    public static final Palette DARK = new Palette(
            0xFF2B2B30, 0xFF45454C, 0xFF18181B, 0xFF1E1E22, 0xFF0F0F12,
            0xFFD8D8DC, 0xFF6FCF73, 0xFFFF6B6B);

    /**
     * Where vanilla's chest window is filled, away from its frame and its
     * slots: the patch a dark-mode pack darkens first.
     */
    private static final ResourceLocation PROBE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    /** Whether the resource packs darken vanilla's windows; null until looked at. */
    private static Boolean packsAreDark;

    /** The palette to draw with now. */
    public static Palette current() {
        UiTheme theme = EconomyClientConfig.SPEC.isLoaded() ? EconomyClientConfig.THEME.get() : UiTheme.AUTO;
        return switch (theme) {
            case LIGHT -> LIGHT;
            case DARK -> DARK;
            case AUTO -> packsAreDark() ? DARK : LIGHT;
        };
    }

    /** Forgets what the resource packs looked like, for when they change. */
    public static void forgetPacks() {
        packsAreDark = null;
    }

    private static boolean packsAreDark() {
        if (packsAreDark == null) {
            packsAreDark = probe();
        }
        return packsAreDark;
    }

    /**
     * Averages a patch of the chest window's fill: dark if it is darker than
     * the middle. Vanilla's is a light gray; a dark-mode pack's is near black.
     * Anything that goes wrong reading it is light, which is what vanilla is.
     */
    private static boolean probe() {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(PROBE);
        if (resource.isEmpty()) {
            return false;
        }
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            // The texture may be drawn at a higher resolution than vanilla's 256.
            int scale = Math.max(1, image.getWidth() / 256);
            long sum = 0;
            int count = 0;
            for (int x = 20; x < 60; x += 4) {
                for (int y = 6; y < 12; y += 2) {
                    int abgr = image.getPixelRGBA(x * scale, y * scale);
                    sum += (abgr & 0xFF) + (abgr >> 8 & 0xFF) + (abgr >> 16 & 0xFF);
                    count += 3;
                }
            }
            return count > 0 && sum / count < 128;
        } catch (Exception e) {
            return false;
        }
    }
}
