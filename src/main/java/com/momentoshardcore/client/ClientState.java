package com.momentoshardcore.client;

import com.momentoshardcore.Evento;
import com.momentoshardcore.net.SyncPayload;

import java.util.List;

public final class ClientState {
    public static int mask = 0;
    public static List<SyncPayload.Entry> kills = List.of();

    private ClientState() {}

    public static boolean isActive(Evento e) {
        return (mask & e.bit()) != 0;
    }

    public static void reset() {
        mask = 0;
        kills = List.of();
    }
}
