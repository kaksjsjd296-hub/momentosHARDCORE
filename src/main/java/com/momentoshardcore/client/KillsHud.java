package com.momentoshardcore.client;

import com.momentoshardcore.Evento;
import com.momentoshardcore.net.SyncPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;

import java.util.List;

/** Tabla "Top kills" estilo marcador lateral, con la cabeza de cada jugador. Solo se ve con el PvP activo. */
public final class KillsHud {
    private KillsHud() {}

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.player == null || mc.getNetworkHandler() == null) return;
        if (!ClientState.isActive(Evento.PVP)) return;

        List<SyncPayload.Entry> list = ClientState.kills;
        if (list.isEmpty()) return;

        TextRenderer tr = mc.textRenderer;
        String title = "Top kills";
        int rowH = 10;

        int maxW = tr.getWidth(title);
        for (SyncPayload.Entry e : list) {
            int w = 11 + tr.getWidth(e.name()) + 10 + tr.getWidth(Integer.toString(e.kills()));
            if (w > maxW) maxW = w;
        }
        int w = maxW + 6;
        int h = (list.size() + 1) * rowH + 2;
        int right = ctx.getScaledWindowWidth() - 1;
        int left = right - w;
        int top = (ctx.getScaledWindowHeight() - h) / 2;

        // fondo: título un poco más oscuro, cuerpo semitransparente (como el marcador de vanilla)
        ctx.fill(left, top, right, top + rowH + 1, 0x60000000);
        ctx.fill(left, top + rowH + 1, right, top + h, 0x50000000);
        ctx.drawText(tr, title, left + (w - tr.getWidth(title)) / 2, top + 2, 0xFFFFFFFF, false);

        int y = top + rowH + 2;
        for (SyncPayload.Entry e : list) {
            PlayerListEntry ple = mc.getNetworkHandler().getPlayerListEntry(e.id());
            if (ple != null) {
                PlayerSkinDrawer.draw(ctx, ple.getSkinTextures(), left + 2, y, 8);
            }
            ctx.drawText(tr, e.name(), left + 13, y, 0xFFFFFFFF, false);
            String k = Integer.toString(e.kills());
            ctx.drawText(tr, k, right - 2 - tr.getWidth(k), y, 0xFFFF5555, false);
            y += rowH;
        }
    }
}
