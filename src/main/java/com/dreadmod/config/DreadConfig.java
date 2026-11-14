package com.dreadmod.config;

import com.dreadmod.Dread;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Конфиг: config/dread.json (создаётся автоматически). */
public class DreadConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static DreadConfig instance = new DreadConfig();

    /** Главный выключатель всех хоррор-событий. */
    public boolean enabled = true;
    /** Звуки за спиной, скрипящие двери, дальние стоны и т.д. */
    public boolean ambientEvents = true;
    /** Разрешить Директору спавнить Сталкера. */
    public boolean stalkerSpawns = true;
    /** Гасить факелы рядом с игроком во время страшных событий. */
    public boolean snuffTorches = true;
    /** Множитель скорости роста страха и частоты событий (0.1 - 5.0). */
    public double intensity = 1.0;
    /** Максимум Сталкеров рядом с одним игроком. */
    public int maxStalkersPerPlayer = 1;
    /** Сколько секунд Сталкер живёт, прежде чем раствориться. */
    public int stalkerLifetimeSeconds = 180;
    /** Сколько секунд непрерывного взгляда прогоняет Сталкера. */
    public int banishStareSeconds = 6;

    public static DreadConfig get() {
        return instance;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("dread.json");
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    DreadConfig loaded = GSON.fromJson(reader, DreadConfig.class);
                    if (loaded != null) {
                        instance = loaded;
                    }
                }
            }
            instance.sanitize();
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException | RuntimeException e) {
            Dread.LOGGER.error("Не удалось прочитать конфиг, используются значения по умолчанию", e);
            instance = new DreadConfig();
        }
    }

    private void sanitize() {
        intensity = MathHelper.clamp(intensity, 0.1, 5.0);
        maxStalkersPerPlayer = MathHelper.clamp(maxStalkersPerPlayer, 0, 5);
        stalkerLifetimeSeconds = MathHelper.clamp(stalkerLifetimeSeconds, 20, 3600);
        banishStareSeconds = MathHelper.clamp(banishStareSeconds, 1, 60);
    }
}
