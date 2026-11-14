package com.dreadmod.registry;

import com.dreadmod.Dread;
import com.dreadmod.item.WardCharmItem;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
    private ModItems() {}

    public static final Item WARD_CHARM = Registry.register(
            Registries.ITEM, Dread.id("ward_charm"),
            new WardCharmItem(new FabricItemSettings().maxDamage(16).rarity(Rarity.UNCOMMON)));

    public static final Item STALKER_SPAWN_EGG = Registry.register(
            Registries.ITEM, Dread.id("stalker_spawn_egg"),
            new SpawnEggItem(ModEntities.STALKER, 0x0B0B0F, 0xE8E8FF, new FabricItemSettings()));

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(WARD_CHARM));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(STALKER_SPAWN_EGG));
    }
}
