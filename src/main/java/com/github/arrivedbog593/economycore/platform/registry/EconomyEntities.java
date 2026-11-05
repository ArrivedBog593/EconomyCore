package com.github.arrivedbog593.economycore.platform.registry;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.platform.entity.Clerk;
import com.github.arrivedbog593.economycore.platform.menu.Counter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EconomyEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, EconomyCore.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<Clerk>> EXCHANGE_CLERK =
            ENTITIES.register("exchange_clerk", () -> clerk("exchange_clerk", Counter.EXCHANGE));

    public static final DeferredHolder<EntityType<?>, EntityType<Clerk>> SHOP_CLERK =
            ENTITIES.register("shop_clerk", () -> clerk("shop_clerk", Counter.SHOP));

    public static final DeferredHolder<EntityType<?>, EntityType<Clerk>> BANKER =
            ENTITIES.register("banker", () -> clerk("banker", Counter.BANK));

    /** A player's size and eye height, since a clerk wears a player's skin. */
    private static EntityType<Clerk> clerk(String id, Counter counter) {
        return EntityType.Builder.<Clerk>of((type, level) -> new Clerk(type, level, counter), MobCategory.MISC)
                .sized(0.6F, 1.8F)
                .eyeHeight(1.62F)
                .fireImmune()
                .clientTrackingRange(10)
                .build(id);
    }

    private EconomyEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        modEventBus.addListener(EconomyEntities::createAttributes);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(EXCHANGE_CLERK.get(), Mob.createMobAttributes().build());
        event.put(SHOP_CLERK.get(), Mob.createMobAttributes().build());
        event.put(BANKER.get(), Mob.createMobAttributes().build());
    }
}
