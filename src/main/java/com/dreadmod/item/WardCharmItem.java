package com.dreadmod.item;

import com.dreadmod.director.HorrorDirector;
import com.dreadmod.entity.StalkerEntity;
import com.dreadmod.registry.ModEntities;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Оберег: изгоняет Сталкеров в радиусе 24 блоков и успокаивает игрока. */
public class WardCharmItem extends Item {
    private static final double RADIUS = 24.0;

    public WardCharmItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(world instanceof ServerWorld serverWorld)) {
            return TypedActionResult.success(stack);
        }

        List<StalkerEntity> stalkers = serverWorld.getEntitiesByType(
                ModEntities.STALKER, user.getBoundingBox().expand(RADIUS), e -> true);

        if (user instanceof ServerPlayerEntity sp) {
            HorrorDirector.addTension(sp, -35);
        }

        if (stalkers.isEmpty()) {
            user.sendMessage(Text.translatable("item.dread.ward_charm.nothing"), true);
            serverWorld.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.PLAYERS, 0.8f, 0.6f);
            user.getItemCooldownManager().set(this, 60);
            return TypedActionResult.success(stack);
        }

        for (StalkerEntity stalker : stalkers) {
            stalker.fadeAway();
        }
        serverWorld.spawnParticles(ParticleTypes.END_ROD, user.getX(), user.getBodyY(0.6), user.getZ(),
                40, 0.6, 0.6, 0.6, 0.05);
        serverWorld.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT,
                SoundCategory.PLAYERS, 1.0f, 0.7f);
        user.sendMessage(Text.translatable("item.dread.ward_charm.banished"), true);
        user.getItemCooldownManager().set(this, 400);
        stack.damage(1, user, p -> p.sendToolBreakStatus(hand));
        return TypedActionResult.success(stack);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.dread.ward_charm.tooltip").formatted(Formatting.GRAY));
    }
}
