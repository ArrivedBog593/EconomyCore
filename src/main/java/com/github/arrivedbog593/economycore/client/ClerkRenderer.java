package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.platform.entity.Clerk;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Draws a clerk as a player wearing its outfit.
 * <p>
 * Holds both of vanilla's player models and picks one per frame, since a
 * skin drawn for three-pixel arms looks broken on four-pixel ones and the
 * outfit can change while the clerk is on screen.
 * <p>
 * A mob's renderer and not a player's: the player one shows every name
 * above every head, which left a row of clerks under a pile of overlapping
 * "Exchange Clerk". Like any mob, a clerk shows a name only once given one.
 */
public class ClerkRenderer extends MobRenderer<Clerk, PlayerModel<Clerk>> {

    private final PlayerModel<Clerk> wide;
    private final PlayerModel<Clerk> slim;

    public ClerkRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = model;
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(@NotNull Clerk clerk, float entityYaw, float partialTicks, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffer, int packedLight) {
        model = clerk.look().slim() ? slim : wide;
        super.render(clerk, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /** A player's own scale, so a clerk stands as tall as the people it serves. */
    @Override
    protected void scale(@NotNull Clerk clerk, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull Clerk clerk) {
        return clerk.look().texture();
    }
}
