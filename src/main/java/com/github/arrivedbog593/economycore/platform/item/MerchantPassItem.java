package com.github.arrivedbog593.economycore.platform.item;

import com.github.arrivedbog593.economycore.platform.EconomyConfig;
import com.github.arrivedbog593.economycore.platform.EconomyData;
import com.github.arrivedbog593.economycore.platform.PassWatch;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A merchant's pass: used up for so many real hours of privileges, as
 * SkyBlock's booster cookie is.
 * <p>
 * Using one asks first. The first use only says what it will do; a second
 * within a few seconds spends it. A pass is bought with credits, and a
 * right-click meant for something else should not cost the player one.
 * <p>
 * The hours run while the player is offline too, and a second pass adds to
 * the first rather than replacing it. Where the privileges are granted reads
 * {@link com.github.arrivedbog593.economycore.api.MerchantPass}.
 */
public class MerchantPassItem extends Item {

    /** How long after the first use the second one still counts as "yes". */
    private static final long CONFIRM_MILLIS = 4_000;

    /** Players who used a pass once and have not confirmed yet, and until when. */
    private static final Map<UUID, Long> ARMED = new ConcurrentHashMap<>();

    public MerchantPassItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                           @NotNull InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(held);
        }
        long now = System.currentTimeMillis();
        int hours = EconomyConfig.PASS_HOURS.get();
        Long armedUntil = ARMED.get(player.getUUID());
        if (armedUntil == null || armedUntil < now) {
            ARMED.put(player.getUUID(), now + CONFIRM_MILLIS);
            serverPlayer.displayClientMessage(Component.translatable("economycore.pass.confirm", hours)
                    .withStyle(ChatFormatting.GOLD), true);
            return InteractionResultHolder.consume(held);
        }
        ARMED.remove(player.getUUID());
        long until = EconomyData.get(serverPlayer.server).extendPass(player.getUUID(), hours * 3_600_000L, now);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        serverPlayer.sendSystemMessage(Component.translatable("economycore.pass.activated",
                PassWatch.describe(until - now)).withStyle(ChatFormatting.GOLD));
        PassWatch.changed(serverPlayer);
        return InteractionResultHolder.consume(held);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> lines, @NotNull TooltipFlag flag) {
        int hours = EconomyConfig.SPEC.isLoaded() ? EconomyConfig.PASS_HOURS.get() : 96;
        lines.add(Component.translatable("economycore.pass.tooltip.duration", hours).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("economycore.pass.tooltip.commands").withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable("economycore.pass.tooltip.death").withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable("economycore.pass.tooltip.stacks").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("economycore.pass.tooltip.use").withStyle(ChatFormatting.YELLOW));
    }
}
