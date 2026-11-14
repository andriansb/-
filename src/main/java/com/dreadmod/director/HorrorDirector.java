package com.dreadmod.director;

import com.dreadmod.config.DreadConfig;
import com.dreadmod.entity.StalkerEntity;
import com.dreadmod.registry.ModEntities;
import com.dreadmod.util.SoundUtil;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * «Режиссёр» ужаса. Для каждого игрока копит уровень страха («напряжение»):
 * растёт в темноте, под землёй и ночью, падает на свету. Чем выше напряжение,
 * тем чаще звуки за спиной и тем вероятнее появление Сталкера.
 */
public final class HorrorDirector {
    private static final Map<UUID, FearState> STATES = new HashMap<>();
    private static final List<Scheduled> TASKS = new ArrayList<>();

    private HorrorDirector() {}

    private static final class FearState {
        double tension;
        long nextAmbient;
        long nextStalker;
        long nextBeat;

        FearState(long now) {
            this.nextAmbient = now + 600;
            this.nextStalker = now + 2400;
        }
    }

    private record Scheduled(long runAt, Runnable task) {}

    // ------------------------------------------------------------------ setup

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(HorrorDirector::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            TASKS.clear();
        });
    }

    private static FearState state(ServerPlayerEntity p) {
        return STATES.computeIfAbsent(p.getUuid(), id -> new FearState(p.server.getTicks()));
    }

    public static double getTension(ServerPlayerEntity p) {
        return state(p).tension;
    }

    public static void setTension(ServerPlayerEntity p, double value) {
        state(p).tension = MathHelper.clamp(value, 0.0, 100.0);
    }

    public static void addTension(ServerPlayerEntity p, double delta) {
        setTension(p, state(p).tension + delta);
    }

    private static void schedule(long runAt, Runnable task) {
        TASKS.add(new Scheduled(runAt, task));
    }

    private static boolean valid(ServerPlayerEntity p) {
        return p.isAlive() && !p.isRemoved() && p.networkHandler != null;
    }

    // ------------------------------------------------------------------- tick

    private static void onServerTick(MinecraftServer server) {
        long now = server.getTicks();
        if (!TASKS.isEmpty()) {
            List<Scheduled> due = new ArrayList<>();
            for (Scheduled s : TASKS) {
                if (s.runAt() <= now) {
                    due.add(s);
                }
            }
            TASKS.removeAll(due);
            for (Scheduled s : due) {
                s.task().run();
            }
        }

        DreadConfig cfg = DreadConfig.get();
        if (!cfg.enabled) {
            return;
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || !p.isAlive()) {
                continue;
            }
            FearState st = state(p);
            if (now % 20 == 0) {
                updateFear(p, st, now, cfg);
            }
            heartbeat(p, st, now);
        }
    }

    private static void updateFear(ServerPlayerEntity p, FearState st, long now, DreadConfig cfg) {
        ServerWorld w = p.getServerWorld();
        if (w.getRegistryKey() != World.OVERWORLD) {
            st.tension = Math.max(0, st.tension - 2);
            return;
        }
        BlockPos pos = p.getBlockPos();
        int light = w.getLightLevel(pos);
        boolean skyVisible = w.isSkyVisible(pos);
        boolean underground = !skyVisible && pos.getY() < w.getSeaLevel();

        double delta;
        if (light <= 4) {
            delta = 1.5;
        } else if (light <= 8) {
            delta = 0.6;
        } else if (light >= 12) {
            delta = -2.0;
        } else {
            delta = -0.6;
        }
        if (underground) {
            delta += 0.6;
        }
        if (w.isNight() && skyVisible) {
            delta += 0.4;
        }
        if (delta > 0) {
            delta *= cfg.intensity;
        }
        st.tension = MathHelper.clamp(st.tension + delta, 0.0, 100.0);

        Random r = p.getRandom();

        if (cfg.ambientEvents && st.tension >= 15 && now >= st.nextAmbient) {
            triggerAmbient(p);
            double mult = cfg.intensity * (0.6 + st.tension / 60.0);
            st.nextAmbient = now + (long) ((400 + r.nextInt(1200)) / mult);
        }

        if (cfg.stalkerSpawns && st.tension >= 55 && now >= st.nextStalker) {
            boolean dark = light <= 7;
            boolean fits = underground || w.isNight();
            if (dark && fits && countStalkers(w, p) < cfg.maxStalkersPerPlayer) {
                if (spawnStalker(p, 22, 32)) {
                    st.tension -= 25;
                    st.nextStalker = now + (long) ((3600 + r.nextInt(6000)) / cfg.intensity);
                }
            }
        }
    }

    private static int countStalkers(ServerWorld w, ServerPlayerEntity p) {
        return w.getEntitiesByType(ModEntities.STALKER, p.getBoundingBox().expand(128), e -> true).size();
    }

    /** Двойной «стук сердца», который учащается, когда Сталкер близко. */
    private static void heartbeat(ServerPlayerEntity p, FearState st, long now) {
        if (now < st.nextBeat) {
            return;
        }
        ServerWorld w = p.getServerWorld();
        double best = Double.MAX_VALUE;
        for (StalkerEntity s : w.getEntitiesByType(ModEntities.STALKER, p.getBoundingBox().expand(32), e -> true)) {
            best = Math.min(best, s.squaredDistanceTo(p));
        }
        if (best == Double.MAX_VALUE) {
            st.nextBeat = now + 20;
            return;
        }
        double dist = Math.sqrt(best);
        float closeness = (float) MathHelper.clamp(1.0 - dist / 32.0, 0.0, 1.0);
        SoundUtil.playTo(p, SoundEvents.ENTITY_WARDEN_HEARTBEAT, p.getX(), p.getY(), p.getZ(),
                0.5f + 0.9f * closeness, 0.9f + 0.3f * closeness);
        st.nextBeat = now + (long) MathHelper.clamp(14 + dist * 1.5, 14, 55);
    }

    // ----------------------------------------------------------------- events

    /** Запускает случайное страшное событие для игрока. */
    public static void triggerAmbient(ServerPlayerEntity p) {
        Random r = p.getRandom();
        double tension = state(p).tension;
        int roll = r.nextInt(tension >= 40 ? 7 : 5);
        switch (roll) {
            case 0 -> footsteps(p);
            case 1 -> doorCreak(p);
            case 2 -> whisper(p);
            case 3 -> distantMoan(p);
            case 4 -> chestCreak(p);
            case 5 -> {
                if (!DreadConfig.get().snuffTorches || !snuffTorch(p)) {
                    whisper(p);
                }
            }
            default -> darknessPulse(p);
        }
    }

    private static Vec3d behind(ServerPlayerEntity p, double dist, double side) {
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        Vec3d right = new Vec3d(-flat.z, 0, flat.x);
        return p.getPos().subtract(flat.multiply(dist)).add(right.multiply(side));
    }

    private static void footsteps(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        long now = p.server.getTicks();
        Random r = p.getRandom();
        BlockState under = w.getBlockState(p.getBlockPos().down());
        SoundEvent step = under.getSoundGroup().getStepSound();
        int steps = 6 + r.nextInt(6);
        double side = (r.nextDouble() - 0.5) * 4.0;
        for (int i = 0; i < steps; i++) {
            final double dist = 12.0 - i * (8.0 / steps);
            final int idx = i;
            schedule(now + i * 10L, () -> {
                if (!valid(p)) {
                    return;
                }
                Vec3d pos = behind(p, dist, side);
                SoundUtil.playTo(p, step, pos.x, p.getY(), pos.z, 0.8f, 0.85f + (idx % 2) * 0.1f);
            });
        }
    }

    private static void doorCreak(ServerPlayerEntity p) {
        Random r = p.getRandom();
        Vec3d pos = behind(p, 8 + r.nextInt(6), (r.nextDouble() - 0.5) * 8.0);
        SoundUtil.playTo(p, SoundEvents.BLOCK_WOODEN_DOOR_OPEN, pos.x, p.getY(), pos.z, 1.0f, 0.8f);
        long at = p.server.getTicks() + 25 + r.nextInt(40);
        schedule(at, () -> {
            if (valid(p)) {
                SoundUtil.playTo(p, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, pos.x, p.getY(), pos.z, 1.0f, 0.8f);
            }
        });
    }

    private static void chestCreak(ServerPlayerEntity p) {
        Random r = p.getRandom();
        Vec3d pos = behind(p, 6 + r.nextInt(6), (r.nextDouble() - 0.5) * 8.0);
        SoundUtil.playTo(p, SoundEvents.BLOCK_CHEST_OPEN, pos.x, p.getY(), pos.z, 0.9f, 0.7f);
        schedule(p.server.getTicks() + 40 + r.nextInt(30), () -> {
            if (valid(p)) {
                SoundUtil.playTo(p, SoundEvents.BLOCK_CHEST_CLOSE, pos.x, p.getY(), pos.z, 0.9f, 0.7f);
            }
        });
    }

    private static void whisper(ServerPlayerEntity p) {
        Vec3d pos = behind(p, 2.5, (p.getRandom().nextDouble() - 0.5) * 2.0);
        SoundUtil.playTo(p, SoundEvents.ENTITY_ENDERMAN_STARE, pos.x, p.getEyeY(), pos.z, 0.6f, 0.5f);
    }

    private static void distantMoan(ServerPlayerEntity p) {
        Random r = p.getRandom();
        Vec3d pos = behind(p, 18 + r.nextInt(10), (r.nextDouble() - 0.5) * 30.0);
        switch (r.nextInt(3)) {
            case 0 -> SoundUtil.playTo(p, SoundEvents.ENTITY_GHAST_AMBIENT, pos.x, p.getY(), pos.z, 1.0f, 0.5f);
            case 1 -> SoundUtil.playTo(p, SoundEvents.ENTITY_PHANTOM_AMBIENT, pos.x, p.getY(), pos.z, 1.0f, 0.6f);
            default -> SoundUtil.playTo(p, SoundEvents.ENTITY_ZOMBIE_VILLAGER_AMBIENT, pos.x, p.getY(), pos.z, 1.0f, 0.4f);
        }
    }

    private static void darknessPulse(ServerPlayerEntity p) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, true, false, false));
        SoundUtil.playTo(p, SoundEvents.ENTITY_WARDEN_HEARTBEAT, p.getX(), p.getY(), p.getZ(), 1.2f, 0.8f);
        SoundUtil.playTo(p, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE, p.getX(), p.getY(), p.getZ(), 0.7f, 0.9f);
    }

    /** Гасит случайный факел рядом с игроком. Возвращает false, если факелов нет. */
    private static boolean snuffTorch(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        BlockPos c = p.getBlockPos();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos bp : BlockPos.iterate(c.add(-8, -3, -8), c.add(8, 3, 8))) {
            BlockState s = w.getBlockState(bp);
            if (s.isOf(Blocks.TORCH) || s.isOf(Blocks.WALL_TORCH)
                    || s.isOf(Blocks.SOUL_TORCH) || s.isOf(Blocks.SOUL_WALL_TORCH)) {
                found.add(bp.toImmutable());
            }
        }
        if (found.isEmpty()) {
            return false;
        }
        BlockPos target = found.get(p.getRandom().nextInt(found.size()));
        w.breakBlock(target, true);
        w.playSound(null, target, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.8f, 1.0f);
        return true;
    }

    // ----------------------------------------------------------------- spawn

    /** Спавнит Сталкера на расстоянии [min, max] блоков от игрока, предпочитая место вне поля зрения. */
    public static boolean spawnStalker(ServerPlayerEntity p, double min, double max) {
        ServerWorld w = p.getServerWorld();
        BlockPos pos = findSpawnPos(w, p, min, max);
        if (pos == null) {
            return false;
        }
        StalkerEntity stalker = ModEntities.STALKER.create(w);
        if (stalker == null) {
            return false;
        }
        stalker.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                p.getRandom().nextFloat() * 360.0f, 0.0f);
        stalker.setTarget(p);
        w.spawnEntity(stalker);
        return true;
    }

    private static BlockPos findSpawnPos(ServerWorld w, ServerPlayerEntity p, double min, double max) {
        Random r = p.getRandom();
        Vec3d look = p.getRotationVec(1.0f).normalize();
        for (int attempt = 0; attempt < 24; attempt++) {
            double ang = r.nextDouble() * Math.PI * 2.0;
            double d = min + r.nextDouble() * (max - min);
            double dx = Math.cos(ang) * d;
            double dz = Math.sin(ang) * d;
            // первые попытки - только вне поля зрения игрока
            if (attempt < 14 && look.dotProduct(new Vec3d(dx, 0, dz).normalize()) > 0.5) {
                continue;
            }
            int x = MathHelper.floor(p.getX() + dx);
            int z = MathHelper.floor(p.getZ() + dz);
            if (!w.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y0 = p.getBlockY();
            for (int dy = 4; dy >= -6; dy--) {
                BlockPos pos = new BlockPos(x, y0 + dy, z);
                BlockPos below = pos.down();
                if (w.getBlockState(below).isSolidBlock(w, below)
                        && w.isAir(pos) && w.isAir(pos.up()) && w.isAir(pos.up(2))) {
                    return pos;
                }
            }
        }
        return null;
    }
}
