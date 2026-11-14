package com.dreadmod;

import com.dreadmod.command.DreadCommand;
import com.dreadmod.config.DreadConfig;
import com.dreadmod.director.HorrorDirector;
import com.dreadmod.registry.ModEntities;
import com.dreadmod.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dread implements ModInitializer {
    public static final String MOD_ID = "dread";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        DreadConfig.load();
        ModEntities.register();
        ModItems.register();
        HorrorDirector.register();
        CommandRegistrationCallback.EVENT.register(DreadCommand::register);
        LOGGER.info("Dread загружен. Не оглядывайтесь.");
    }
}
