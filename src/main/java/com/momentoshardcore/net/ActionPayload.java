package com.momentoshardcore.net;

import com.momentoshardcore.MomentosHardcore;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Cliente -> servidor: "activa/desactiva este evento" (con un valor opcional: capa o segundos). */
public record ActionPayload(int event, int value) implements CustomPayload {
    public static final CustomPayload.Id<ActionPayload> ID =
            new CustomPayload.Id<ActionPayload>(Identifier.of(MomentosHardcore.MOD_ID, "action"));

    public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC = new PacketCodec<RegistryByteBuf, ActionPayload>() {
        @Override
        public ActionPayload decode(RegistryByteBuf buf) {
            return new ActionPayload(buf.readVarInt(), buf.readInt());
        }

        @Override
        public void encode(RegistryByteBuf buf, ActionPayload p) {
            buf.writeVarInt(p.event());
            buf.writeInt(p.value());
        }
    };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
