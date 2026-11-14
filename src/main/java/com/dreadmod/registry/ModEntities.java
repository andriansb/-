package com.dreadmod.registry;

import com.dreadmod.Dread;
import com.dreadmod.entity.StalkerEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
    private ModEntities() {}

    public static final EntityType<StalkerEntity> STALKER = Registry.register(
            Registries.ENTITY_TYPE,
            Dread.id("stalker"),
            EntityType.Builder.create(StalkerEntity::new, SpawnGroup.MONSTER)
                    .setDimensions(0.6f, 2.3f)
                    .maxTrackingRange(10)
                    .build("stalker"));

    public static void register() {
        FabricDefaultAttributeRegistry.register(STALKER, StalkerEntity.createStalkerAttributes());
    }
}
