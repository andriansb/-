package com.dreadmod.util;

import net.minecraft.registry.Registries;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

public final class SoundUtil {
    private SoundUtil() {}

    /** Проигрывает звук ТОЛЬКО одному игроку в указанной точке мира. */
    public static void playTo(ServerPlayerEntity player, SoundEvent sound,
                              double x, double y, double z, float volume, float pitch) {
        if (player.networkHandler == null) {
            return;
        }
        player.networkHandler.sendPacket(new PlaySoundS2CPacket(
                Registries.SOUND_EVENT.getEntry(sound), SoundCategory.AMBIENT,
                x, y, z, volume, pitch, player.getRandom().nextLong()));
    }
}
