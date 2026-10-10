package com.momentoshardcore.mixin;

import com.momentoshardcore.Evento;
import com.momentoshardcore.MomentosHardcore;
import com.momentoshardcore.client.ClientState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    private static final Identifier MH_UNKNOWN_HEART =
            Identifier.of(MomentosHardcore.MOD_ID, "textures/gui/heart_unknown.png");

    /** Con el evento activo, oculta la barra de vida y dibuja 10 corazones con "?". */
    @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
    private void mh$hideHealth(DrawContext context, PlayerEntity player, int x, int y, int lines,
                               int regeneratingHeartIndex, float maxHealth, int lastHealth, int health,
                               int absorption, boolean blinking, CallbackInfo ci) {
        if (!ClientState.isActive(Evento.HIDE_HEALTH)) return;
        ci.cancel();
        for (int i = 0; i < 10; i++) {
            context.drawTexture(MH_UNKNOWN_HEART, x + i * 8, y, 0, 0, 9, 9, 9, 9);
        }
    }
}
