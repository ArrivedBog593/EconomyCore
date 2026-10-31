package com.github.arrivedbog593.economycore.platform.entity;

import com.github.arrivedbog593.economycore.platform.item.AdminKeyItem;
import com.github.arrivedbog593.economycore.platform.menu.Counter;
import com.github.arrivedbog593.economycore.platform.menu.MenuAnchor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Somebody standing behind a counter: right-click them and they open the
 * exchange or the shop, exactly as the block does.
 * <p>
 * For whoever builds a casino or a market and wants a person at the till
 * rather than a block. Placed like any mob — its spawn egg, or
 * {@code /summon} — and by anybody who can get one, since an egg in survival
 * only comes from creative anyway.
 * <p>
 * Furniture, not a creature. It stays where it was put, faces the way it was
 * put, and only turns its head to whoever is nearest. Nothing a survival
 * player can do hurts or moves it; a player in creative removes it with a
 * hit, the way they would break a block, to put it down again somewhere
 * better. {@code /kill} works too.
 */
public class Clerk extends Mob {

    private static final EntityDataAccessor<Integer> LOOK =
            SynchedEntityData.defineId(Clerk.class, EntityDataSerializers.INT);

    /** How far away a player can be and still have the clerk look at them. */
    private static final float LOOK_DISTANCE = 8.0F;

    /** How far the head turns from the shoulders, short of a full owl. */
    private static final float HEAD_TURN = 70.0F;

    private final Counter counter;

    public Clerk(EntityType<? extends Clerk> type, Level level, Counter counter) {
        super(type, level);
        this.counter = counter;
        setLook(ClerkLook.of(counter).getFirst());
    }

    public Counter counter() {
        return counter;
    }

    public ClerkLook look() {
        return ClerkLook.byOrdinal(entityData.get(LOOK));
    }

    public void setLook(ClerkLook look) {
        entityData.set(LOOK, look.ordinal());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LOOK, ClerkLook.SUIT.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, LOOK_DISTANCE, 1.0F));
    }

    /**
     * Faces whoever put it down, squared to the nearest side of a block, and
     * picks one of its counter's outfits.
     * <p>
     * Squared because a clerk goes behind a counter, and a counter runs along
     * the grid. Vanilla spins anything hatched from an egg to a random angle,
     * which would leave every clerk looking slightly off at the wall.
     * <p>
     * Not called for a clerk summoned with data of its own, nor before an
     * egg's stored data is applied, so an outfit given either way survives.
     */
    @Override
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level, @NotNull DifficultyInstance difficulty,
                                        @NotNull MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        setLook(ClerkLook.random(counter, random));

        Player placer = level.getNearestPlayer(this, LOOK_DISTANCE);
        if (placer != null) {
            double dx = placer.getX() - getX();
            double dz = placer.getZ() - getZ();
            float facing = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            face(Math.round(facing / 90.0F) * 90.0F);
        }
        return data;
    }

    private void face(float yaw) {
        setYRot(yaw);
        yRotO = yaw;
        yBodyRot = yaw;
        yBodyRotO = yaw;
        yHeadRot = yaw;
        yHeadRotO = yaw;
    }

    // --- Using it ------------------------------------------------------------------------

    /**
     * Opens the counter, or its settings for a staff key.
     * <p>
     * A name tag never gets here: vanilla applies it first, so naming a clerk
     * works the way it does for any mob. A creative player crouching with an
     * empty hand changes the outfit instead, which is the only way to choose
     * one without typing a summon command.
     */
    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof AdminKeyItem) {
            counter.administer(serverPlayer, held, MenuAnchor.entity(this));
            return InteractionResult.CONSUME;
        }
        if (player.getAbilities().instabuild && player.isShiftKeyDown() && held.isEmpty()) {
            ClerkLook next = look().next(counter);
            setLook(next);
            serverPlayer.displayClientMessage(Component.translatable("economycore.clerk.look",
                    Component.translatable(next.translationKey())), true);
            return InteractionResult.CONSUME;
        }
        counter.open(serverPlayer, MenuAnchor.entity(this));
        return InteractionResult.CONSUME;
    }

    /**
     * Refuses all harm but two: a creative player's own hand, which removes
     * the clerk on the spot, and whatever vanilla lets through anything
     * invulnerable — {@code /kill}, falling out of the world.
     * <p>
     * The hand and not anything a creative player fired, so an arrow loosed
     * at something else does not take the cashier with it.
     */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof Player player && player.getAbilities().instabuild) {
            if (!level().isClientSide) {
                level().broadcastEntityEvent(this, EntityEvent.POOF);
                discard();
            }
            return true;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        return false;
    }

    // --- Staying put -----------------------------------------------------------------------

    /**
     * Keeps the shoulders where they were put.
     * <p>
     * A mob's body normally swings round to follow its head after a moment,
     * so a clerk watching somebody walk past the counter would end up with its
     * back to it.
     */
    @Override
    protected @NotNull BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                yBodyRot = getYRot();
            }
        };
    }

    @Override
    public void tick() {
        super.tick();
        // The look goal turns the head as far as it takes to face its target;
        // nothing else holds it to the shoulders while the body stays put.
        yHeadRot = Mth.rotateIfNecessary(yHeadRot, yBodyRot, HEAD_TURN);
    }

    /** Falls if put down in the air, and goes nowhere else. */
    @Override
    public void travel(@NotNull Vec3 travelVector) {
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        super.travel(Vec3.ZERO);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(@NotNull Entity entity) {
    }

    @Override
    protected void doPush(@NotNull Entity entity) {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean ignoreExplosion(@NotNull Explosion explosion) {
        return true;
    }

    @Override
    public boolean canBeAffected(@NotNull MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // --- Saving ------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Look", look().id());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Look")) {
            setLook(ClerkLook.byId(tag.getString("Look"), counter));
        }
    }
}
