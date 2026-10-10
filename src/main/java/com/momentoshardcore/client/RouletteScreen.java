package com.momentoshardcore.client;

import com.momentoshardcore.Evento;
import com.momentoshardcore.MomentosHardcore;
import com.momentoshardcore.net.ActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Menú de la ruleta: fondo transparente, iconos alrededor de la rueda que crecen al pasar el cursor. */
public class RouletteScreen extends Screen {
    private static final int ICON = 32;
    private static final int RADIUS = 82;
    private static final int WHEEL = 64;

    private static Identifier tex(String name) {
        return Identifier.of(MomentosHardcore.MOD_ID, "textures/gui/" + name + ".png");
    }

    private static final Identifier WHEEL_TEX = tex("wheel");

    private record Slot(Evento event, Identifier texture, double angleDeg) {}

    // Ángulos medidos desde arriba, en sentido horario (parecido al dibujo de referencia).
    private static final Slot[] SLOTS = {
            new Slot(Evento.ACID,        tex("acid"),           -67.5),
            new Slot(Evento.LAYER_DOWN,  tex("cloud_down"),     -22.5),
            new Slot(Evento.LAYER_UP,    tex("cloud_up"),        22.5),
            new Slot(Evento.CREEPERS,    tex("creeper"),         67.5),
            new Slot(Evento.MAGMA,       tex("magma"),          112.5),
            new Slot(Evento.HIDE_HEALTH, tex("heart_question"), 157.5),
            new Slot(Evento.PVP,         tex("swords"),         202.5),
            new Slot(Evento.TELEPORT,    tex("teleport"),       247.5),
    };

    private final float[] hover = new float[SLOTS.length];
    private TextFieldWidget input;
    private ButtonWidget okButton;
    private Evento pending = null;

    public RouletteScreen() {
        super(Text.literal("Ruleta"));
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        input = new TextFieldWidget(textRenderer, cx - 40, cy - 6, 80, 18, Text.literal(""));
        input.setMaxLength(6);
        okButton = ButtonWidget.builder(Text.literal("OK"), b -> confirm())
                .dimensions(cx - 25, cy + 16, 50, 18).build();
        addDrawableChild(input);
        addDrawableChild(okButton);
        hideInput();
    }

    private int slotX(int i) {
        return width / 2 + (int) Math.round(RADIUS * Math.sin(Math.toRadians(SLOTS[i].angleDeg())));
    }

    private int slotY(int i) {
        return height / 2 - (int) Math.round(RADIUS * Math.cos(Math.toRadians(SLOTS[i].angleDeg())));
    }

    // ---------------------------------------------------------------- dibujo

    /** Fondo transparente: no dibujamos nada. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cx = width / 2, cy = height / 2;
        ctx.drawTexture(WHEEL_TEX, cx - WHEEL / 2, cy - WHEEL / 2, WHEEL, WHEEL, 0, 0, 32, 32, 32, 32);

        for (int i = 0; i < SLOTS.length; i++) {
            int px = slotX(i), py = slotY(i);
            boolean over = Math.abs(mouseX - px) <= ICON / 2 && Math.abs(mouseY - py) <= ICON / 2;
            hover[i] += ((over ? 1f : 0f) - hover[i]) * 0.35f;
            float scale = 1f + 0.25f * hover[i];

            ctx.getMatrices().push();
            ctx.getMatrices().translate((float) px, (float) py, 0f);
            ctx.getMatrices().scale(scale, scale, 1f);
            Evento ev = SLOTS[i].event();
            if (ev != Evento.TELEPORT && ClientState.isActive(ev)) {
                ctx.fill(-ICON / 2 - 2, -ICON / 2 - 2, ICON / 2 + 2, ICON / 2 + 2, 0x6655FF55);
            }
            ctx.drawTexture(SLOTS[i].texture(), -ICON / 2, -ICON / 2, ICON, ICON, 0, 0, 32, 32, 32, 32);
            ctx.getMatrices().pop();
        }

        if (pending != null) {
            ctx.fill(cx - 50, cy - 36, cx + 50, cy + 38, 0xCC000000);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(promptFor(pending)), cx, cy - 26, 0xFFFFFFFF);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private static String promptFor(Evento ev) {
        return switch (ev) {
            case LAYER_UP -> "Capa segura (ej. 100)";
            case LAYER_DOWN -> "Capa segura (ej. -100)";
            case PVP -> "Duración en segundos";
            default -> "";
        };
    }

    // ---------------------------------------------------------------- entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        for (int i = 0; i < SLOTS.length; i++) {
            if (Math.abs(mouseX - slotX(i)) <= ICON / 2.0 && Math.abs(mouseY - slotY(i)) <= ICON / 2.0) {
                playClick();
                onIcon(SLOTS[i].event());
                return true;
            }
        }
        return false;
    }

    private void onIcon(Evento ev) {
        if (ev.needsValue && !ClientState.isActive(ev)) {
            showInput(ev);
        } else {
            hideInput();
            send(ev, 0); // apagar evento, o acción directa (teletransporte, creepers, etc.)
        }
    }

    private void showInput(Evento ev) {
        pending = ev;
        boolean allowNegative = ev == Evento.LAYER_DOWN;
        String regex = allowNegative ? "-?\\d{0,5}" : "\\d{0,6}";
        input.setTextPredicate(s -> s.matches(regex));
        input.setText(allowNegative ? "-" : "");
        input.visible = true;
        input.active = true;
        okButton.visible = true;
        okButton.active = true;
        setFocused(input);
        input.setFocused(true);
    }

    private void hideInput() {
        pending = null;
        if (input != null) {
            input.setFocused(false);
            input.visible = false;
            input.active = false;
        }
        if (okButton != null) {
            okButton.visible = false;
            okButton.active = false;
        }
    }

    private void confirm() {
        if (pending == null) return;
        String text = input.getText().trim();
        if (text.isEmpty() || text.equals("-")) return;
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return;
        }
        if (pending == Evento.PVP && value <= 0) return;
        Evento ev = pending;
        hideInput();
        send(ev, value);
    }

    private void send(Evento ev, int value) {
        ClientPlayNetworking.send(new ActionPayload(ev.ordinal(), value));
    }

    private void playClick() {
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pending != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirm();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                hideInput();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
