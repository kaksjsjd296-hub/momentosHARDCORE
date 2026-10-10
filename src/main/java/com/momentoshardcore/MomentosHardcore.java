package com.momentoshardcore;

import com.momentoshardcore.net.ActionPayload;
import com.momentoshardcore.net.SyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class MomentosHardcore implements ModInitializer {
    public static final String MOD_ID = "momentoshardcore";
    public static Item RULETA;

    @Override
    public void onInitialize() {
        RULETA = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "ruleta"),
                new RouletteItem(new Item.Settings().maxCount(1)));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(RULETA));

        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID,
                (payload, context) -> EventManager.handleAction(context.player(), payload.event(), payload.value()));

        EventManager.init();
    }
}
