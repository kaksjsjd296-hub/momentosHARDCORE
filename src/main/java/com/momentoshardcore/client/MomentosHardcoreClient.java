package com.momentoshardcore.client;

import com.momentoshardcore.RouletteItem;
import com.momentoshardcore.net.SyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

public class MomentosHardcoreClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RouletteItem.OPEN_SCREEN = () -> MinecraftClient.getInstance().setScreen(new RouletteScreen());

        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, context) -> {
            ClientState.mask = payload.mask();
            ClientState.kills = payload.kills();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> KillsHud.render(drawContext));
    }
}
