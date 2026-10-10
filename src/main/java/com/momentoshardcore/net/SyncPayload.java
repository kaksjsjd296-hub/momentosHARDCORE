package com.momentoshardcore.net;

import com.momentoshardcore.MomentosHardcore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Servidor -> cliente: eventos activos (bitmask) + tabla de top kills (solo con PvP activo). */
public record SyncPayload(int mask, List<Entry> kills) implements CustomPayload {
    public record Entry(UUID id, String name, int kills) {}

    public static final CustomPayload.Id<SyncPayload> ID =
            new CustomPayload.Id<SyncPayload>(Identifier.of(MomentosHardcore.MOD_ID, "sync"));

    public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = new PacketCodec<RegistryByteBuf, SyncPayload>() {
        @Override
        public SyncPayload decode(RegistryByteBuf buf) {
            int mask = buf.readVarInt();
            int n = buf.readVarInt();
            List<Entry> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                UUID id = buf.readUuid();
                String name = buf.readString(32);
                int k = buf.readVarInt();
                list.add(new Entry(id, name, k));
            }
            return new SyncPayload(mask, list);
        }

        @Override
        public void encode(RegistryByteBuf buf, SyncPayload p) {
            buf.writeVarInt(p.mask());
            buf.writeVarInt(p.kills().size());
            for (Entry e : p.kills()) {
                buf.writeUuid(e.id());
                buf.writeString(e.name(), 32);
                buf.writeVarInt(e.kills());
            }
        }
    };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
