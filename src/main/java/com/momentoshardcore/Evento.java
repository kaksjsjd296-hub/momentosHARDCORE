package com.momentoshardcore;

/** Eventos de la ruleta. El orden (ordinal) es el id que viaja por red. */
public enum Evento {
    ACID(false),
    LAYER_UP(true),
    LAYER_DOWN(true),
    MAGMA(false),
    PVP(true),
    HIDE_HEALTH(false),
    CREEPERS(false),
    TELEPORT(false); // acción de un solo uso, no se "activa"

    /** true si al activarlo hay que escribir un número (capa o segundos). */
    public final boolean needsValue;

    Evento(boolean needsValue) {
        this.needsValue = needsValue;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static Evento byId(int id) {
        Evento[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }
}
