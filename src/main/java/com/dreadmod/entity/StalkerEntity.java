package com.dreadmod.entity;

import com.dreadmod.config.DreadConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Сталкер. Замирает, пока на него смотрят (пока он в поле зрения и виден хотя бы одному игроку),
 * и стремительно приближается, как только вы отвернулись. Долгий прямой взгляд прогоняет его.
 */
public class StalkerEntity extends HostileEntity {
    /** Косинус половины угла «видимости» (~57°). */
    private static final double VIEW_COS = 0.55;
    private static final double VIEW_RANGE = 64.0;

    private boolean watched;
    private int watchedTicks;

    public StalkerEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 10;
    }

    public static DefaultAttributeContainer.Builder createStalkerAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 60.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.34)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new StalkGoal(this));
        this.goalSelector.add(6, new WanderAroundGoal(this, 0.6));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, false, false, null));
    }

    public boolean isWatched() {
        return watched;
    }

    @Override
    public void tick() {
        if (!getWorld().isClient) {
            updateWatchState();
        }
        super.tick();
        if (!getWorld().isClient && this.age > DreadConfig.get().stalkerLifetimeSeconds * 20) {
            fadeAway();
        }
    }

    private void updateWatchState() {
        boolean seen = false;
        for (PlayerEntity player : getWorld().getPlayers()) {
            if (isWatchedBy(player)) {
                seen = true;
                break;
            }
        }
        this.watched = seen;
        if (seen) {
            this.watchedTicks++;
            if (this.watchedTicks >= DreadConfig.get().banishStareSeconds * 20) {
                fadeAway();
            }
        } else if (this.watchedTicks > 0 && this.age % 4 == 0) {
            this.watchedTicks--;
        }
    }

    private boolean isWatchedBy(PlayerEntity player) {
        if (player.isSpectator() || player.hasStatusEffect(StatusEffects.BLINDNESS)) {
            return false;
        }
        if (player.squaredDistanceTo(this) > VIEW_RANGE * VIEW_RANGE) {
            return false;
        }
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d to = new Vec3d(getX() - player.getX(), getBodyY(0.6) - player.getEyeY(), getZ() - player.getZ());
        double dist = to.length();
        if (dist < 1.0E-4) {
            return true;
        }
        return look.dotProduct(to.multiply(1.0 / dist)) > VIEW_COS && player.canSee(this);
    }

    /** Пока на Сталкера смотрят - он полностью обездвижен (ИИ не работает). */
    @Override
    protected boolean isImmobile() {
        return watched || super.isImmobile();
    }

    /** Растворяется в дыму (по таймеру жизни или от долгого взгляда). */
    public void fadeAway() {
        if (getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getBodyY(0.5), getZ(), 30, 0.3, 0.9, 0.3, 0.02);
            sw.spawnParticles(ParticleTypes.SOUL, getX(), getBodyY(0.5), getZ(), 12, 0.3, 0.9, 0.3, 0.03);
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDERMAN_DEATH, SoundCategory.HOSTILE, 0.8f, 0.4f);
        }
        this.discard();
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit && target instanceof LivingEntity living) {
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0, false, false), this);
        }
        return hit;
    }

    @Override
    protected float getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions) {
        return 2.05f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ENDERMAN_DEATH;
    }

    /** Преследование: рывок к цели, когда на Сталкера никто не смотрит. */
    private static class StalkGoal extends Goal {
        private final StalkerEntity mob;
        private int repath;
        private int attackCooldown;

        StalkGoal(StalkerEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            LivingEntity target = mob.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            mob.getLookControl().lookAt(target, 30.0f, 30.0f);
            double distSq = mob.squaredDistanceTo(target);
            if (--repath <= 0) {
                repath = 4;
                double speed = distSq < 144.0 ? 1.4 : 1.0;
                mob.getNavigation().startMovingTo(target, speed);
            }
            if (attackCooldown > 0) {
                attackCooldown--;
            }
            if (attackCooldown <= 0 && distSq <= 2.6 * 2.6 && mob.canSee(target)) {
                attackCooldown = 10;
                mob.swingHand(Hand.MAIN_HAND);
                mob.tryAttack(target);
            }
        }
    }
}
