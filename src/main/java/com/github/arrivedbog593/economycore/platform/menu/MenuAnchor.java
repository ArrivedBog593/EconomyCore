package com.github.arrivedbog593.economycore.platform.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Where an exchange or shop screen was opened from, and so what keeps it open.
 * <p>
 * The screens used to be tied to a block position, which left a clerk or a
 * command no way to open them at all. The screen and its logic are the same
 * wherever it was opened; what changes is only the door: walk away from a
 * block or a clerk and the screen closes, as a chest's does; open it by
 * command and there is nothing to walk away from.
 * <p>
 * Server side only. The client is never asked whether a menu is still valid,
 * so its copy of a menu carries {@link #ANYWHERE} whatever the server's says.
 */
public sealed interface MenuAnchor {

    /** How far a player may stand from a block or a clerk and keep using it: vanilla's reach for containers. */
    double REACH = 4.0;

    /** Opened by command, or the client's copy of any menu. */
    MenuAnchor ANYWHERE = new Anywhere();

    static MenuAnchor block(Level level, BlockPos pos, Block block) {
        return new AtBlock(level, pos.immutable(), block);
    }

    static MenuAnchor entity(Entity entity) {
        return new AtEntity(entity);
    }

    /** Whether this player may keep the screen open. Asked every tick. */
    boolean stillValid(Player who);

    /** A block, which has to still be there and within reach. */
    record AtBlock(Level level, BlockPos pos, Block block) implements MenuAnchor {
        @Override
        public boolean stillValid(Player who) {
            return who.level() == level
                    && level.getBlockState(pos).is(block)
                    && who.canInteractWithBlock(pos, REACH);
        }
    }

    /** A clerk, which has to still be alive and within reach. */
    record AtEntity(Entity entity) implements MenuAnchor {
        @Override
        public boolean stillValid(Player who) {
            return entity.isAlive()
                    && who.level() == entity.level()
                    && who.canInteractWithEntity(entity, REACH);
        }
    }

    /** Nothing to walk away from. */
    record Anywhere() implements MenuAnchor {
        @Override
        public boolean stillValid(Player who) {
            return true;
        }
    }
}
