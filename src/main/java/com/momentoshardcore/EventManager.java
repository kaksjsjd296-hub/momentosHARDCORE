package com.momentoshardcore;

import com.momentoshardcore.net.SyncPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Toda la lógica de servidor de los eventos de la ruleta. */
public final class EventManager {
    // ---- Ajustes fáciles de cambiar ----
    private static final float ACID_DAMAGE = 1.0f;          // por segundo, a quien esté expuesto al cielo
    private static final float LAYER_DAMAGE = 2.0f;         // por segundo, a quien esté bajo la capa
    private static final int MAGMA_PER_SECOND = 3;          // cubos de magma que caen por jugador cada segundo
    private static final int CREEPER_RAIN_INTERVAL = 40;    // ticks entre cada tanda de creepers cayendo
    private static final int CREEPER_AT_PLAYER_INTERVAL = 200; // ticks entre creepers que aparecen en el jugador
    private static final int TELEPORT_RADIUS = 2000;        // bloques desde el 0,0

    private static final Random RNG = new Random();

    private static boolean acid, magma, creepers, hideHealth;
    private static boolean layerUpOn, layerDownOn;
    private static int layerUp = 0, layerDown = -1;

    private static boolean pvpOn, prevPvp;
    private static int pvpTicksLeft, pvpTotalTicks;
    private static ServerBossBar bar;
    private static final Map<UUID, Integer> kills = new HashMap<>();

    private static int tick = 0;

    private EventManager() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(EventManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer(), server));
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killed) -> {
            if (pvpOn && entity instanceof ServerPlayerEntity killer && killed instanceof PlayerEntity) {
                kills.merge(killer.getUuid(), 1, Integer::sum);
            }
        });
    }

    private static void reset() {
        acid = magma = creepers = hideHealth = false;
        layerUpOn = layerDownOn = false;
        pvpOn = false;
        bar = null;
        kills.clear();
        tick = 0;
    }

    // ------------------------------------------------------------------ acciones

    public static void handleAction(ServerPlayerEntity player, int id, int value) {
        MinecraftServer server = player.getServer();
        Evento ev = Evento.byId(id);
        if (server == null || ev == null) return;

        if (!(player.hasPermissionLevel(2) || server.isSingleplayer())) {
            player.sendMessage(Text.literal("No tienes permiso para usar la ruleta."), true);
            return;
        }

        switch (ev) {
            case ACID -> {
                acid = !acid;
                if (!acid) server.getOverworld().setWeather(6000, 0, false, false);
            }
            case LAYER_UP -> {
                layerUpOn = !layerUpOn;
                if (layerUpOn) layerUp = Math.abs(value);
            }
            case LAYER_DOWN -> {
                layerDownOn = !layerDownOn;
                if (layerDownOn) layerDown = Math.min(-1, -Math.abs(value));
            }
            case MAGMA -> magma = !magma;
            case CREEPERS -> creepers = !creepers;
            case HIDE_HEALTH -> hideHealth = !hideHealth;
            case PVP -> {
                if (pvpOn) stopPvp(server);
                else startPvp(server, value);
            }
            case TELEPORT -> teleportAll(server);
        }
        syncAll(server);
    }

    private static void startPvp(MinecraftServer server, int seconds) {
        if (seconds <= 0) return;
        seconds = Math.min(seconds, 86400);
        pvpOn = true;
        prevPvp = server.isPvpEnabled();
        server.setPvpEnabled(true);
        pvpTotalTicks = pvpTicksLeft = seconds * 20;
        kills.clear();
        bar = new ServerBossBar(Text.literal(formatTime(seconds)), BossBar.Color.RED, BossBar.Style.PROGRESS);
        bar.setPercent(1.0f);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            bar.addPlayer(p);
            giveGlow(p);
        }
    }

    private static void stopPvp(MinecraftServer server) {
        pvpOn = false;
        server.setPvpEnabled(prevPvp);
        if (bar != null) {
            bar.clearPlayers();
            bar = null;
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.removeStatusEffect(StatusEffects.GLOWING);
        }
        kills.clear();
    }

    private static void giveGlow(ServerPlayerEntity p) {
        // Sin equipo, el brillo es blanco por defecto.
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, false, false, false));
    }

    private static String formatTime(int seconds) {
        return String.format("PVP  %02d:%02d", seconds / 60, seconds % 60);
    }

    private static void teleportAll(MinecraftServer server) {
        for (ServerPlayerEntity p : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
            ServerWorld w = p.getServerWorld();
            if (w.getRegistryKey() != World.OVERWORLD) continue;
            for (int i = 0; i < 25; i++) {
                int x = RNG.nextInt(TELEPORT_RADIUS * 2 + 1) - TELEPORT_RADIUS;
                int z = RNG.nextInt(TELEPORT_RADIUS * 2 + 1) - TELEPORT_RADIUS;
                if (!w.getWorldBorder().contains(new BlockPos(x, 64, z))) continue;
                int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos below = new BlockPos(x, y - 1, z);
                if (!w.getFluidState(below).isEmpty() || !w.getFluidState(below.up()).isEmpty()) continue;
                p.requestTeleport(x + 0.5, y, z + 0.5);
                break;
            }
        }
    }

    // ------------------------------------------------------------------ tick

    private static boolean vulnerable(ServerPlayerEntity p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator();
    }

    private static void tick(MinecraftServer server) {
        int t = ++tick;
        boolean second = t % 20 == 0;
        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());

        if (acid) {
            if (second) {
                ServerWorld ow = server.getOverworld();
                if (!ow.isRaining()) ow.setWeather(0, 24000, true, false);
            }
            for (ServerPlayerEntity p : players) {
                ServerWorld w = p.getServerWorld();
                if (w.getRegistryKey() != World.OVERWORLD) continue;
                if (!w.isSkyVisible(p.getBlockPos().up())) continue;
                if (t % 10 == 0) {
                    w.spawnParticles(new DustParticleEffect(new Vector3f(0.35f, 1.0f, 0.15f), 1.2f),
                            p.getX(), p.getY() + 1.5, p.getZ(), 12, 0.6, 0.8, 0.6, 0.0);
                }
                if (second && vulnerable(p)) {
                    p.damage(w.getDamageSources().magic(), ACID_DAMAGE);
                }
            }
        }

        if (second && (layerUpOn || layerDownOn)) {
            for (ServerPlayerEntity p : players) {
                if (!vulnerable(p)) continue;
                boolean hurt = (layerUpOn && p.getY() < layerUp) || (layerDownOn && p.getY() < layerDown);
                if (hurt) p.damage(p.getServerWorld().getDamageSources().magic(), LAYER_DAMAGE);
            }
        }

        if (magma && second) {
            for (ServerPlayerEntity p : players) {
                if (p.isSpectator()) continue;
                ServerWorld w = p.getServerWorld();
                if (w.getRegistryKey() == World.OVERWORLD) spawnMagma(w, p);
            }
        }

        if (creepers) {
            for (ServerPlayerEntity p : players) {
                if (p.isSpectator()) continue;
                ServerWorld w = p.getServerWorld();
                if (t % CREEPER_RAIN_INTERVAL == 0 && w.getRegistryKey() == World.OVERWORLD) {
                    spawnCreeperRain(w, p);
                }
                if (t % CREEPER_AT_PLAYER_INTERVAL == 0 && countCreepers(w, p) < 30) {
                    spawnCreeper(w, p.getX(), p.getY(), p.getZ(), false);
                }
            }
        }

        if (pvpOn) {
            pvpTicksLeft--;
            if (second || pvpTicksLeft <= 0) {
                int secs = Math.max(0, (pvpTicksLeft + 19) / 20);
                if (bar != null) {
                    bar.setName(Text.literal(formatTime(secs)));
                    bar.setPercent(Math.max(0f, Math.min(1f, pvpTicksLeft / (float) pvpTotalTicks)));
                    for (ServerPlayerEntity p : players) bar.addPlayer(p);
                }
                for (ServerPlayerEntity p : players) giveGlow(p);
            }
            if (pvpTicksLeft <= 0) {
                stopPvp(server);
                syncAll(server);
            } else if (second) {
                syncAll(server);
            }
        }
    }

    // ------------------------------------------------------------------ spawns

    private static void spawnMagma(ServerWorld w, ServerPlayerEntity p) {
        Box box = p.getBoundingBox().expand(64);
        if (w.getEntitiesByType(EntityType.MAGMA_CUBE, box, e -> true).size() >= 40) return;
        for (int i = 0; i < MAGMA_PER_SECOND; i++) {
            double x = p.getX() + (RNG.nextDouble() * 2 - 1) * 40;
            double z = p.getZ() + (RNG.nextDouble() * 2 - 1) * 40;
            int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            double y = Math.min(top + 25, w.getTopY() - 2);
            MagmaCubeEntity cube = EntityType.MAGMA_CUBE.create(w);
            if (cube == null) continue;
            cube.setSize(1 + RNG.nextInt(2), true);
            cube.refreshPositionAndAngles(x, y, z, RNG.nextFloat() * 360f, 0f);
            w.spawnEntity(cube);
        }
    }

    private static int countCreepers(ServerWorld w, ServerPlayerEntity p) {
        return w.getEntitiesByType(EntityType.CREEPER, p.getBoundingBox().expand(64), e -> true).size();
    }

    private static void spawnCreeperRain(ServerWorld w, ServerPlayerEntity p) {
        if (countCreepers(w, p) >= 30) return;
        for (int i = 0; i < 2; i++) {
            double x = p.getX() + (RNG.nextDouble() * 2 - 1) * 24;
            double z = p.getZ() + (RNG.nextDouble() * 2 - 1) * 24;
            int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            double y = Math.min(top + 20, w.getTopY() - 2);
            spawnCreeper(w, x, y, z, true);
        }
    }

    /** Creeper cargado con 1 de vida (medio corazón). Los que caen llevan caída lenta para no morir al aterrizar. */
    private static void spawnCreeper(ServerWorld w, double x, double y, double z, boolean falling) {
        CreeperEntity c = EntityType.CREEPER.create(w);
        if (c == null) return;
        NbtCompound nbt = new NbtCompound();
        c.writeNbt(nbt);
        nbt.putBoolean("powered", true);
        c.readNbt(nbt);
        c.refreshPositionAndAngles(x, y, z, RNG.nextFloat() * 360f, 0f);
        c.setHealth(1.0f);
        if (falling) {
            c.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 200, 0, false, false));
        }
        w.spawnEntity(c);
    }

    // ------------------------------------------------------------------ sync

    private static int mask() {
        int m = 0;
        if (acid) m |= Evento.ACID.bit();
        if (layerUpOn) m |= Evento.LAYER_UP.bit();
        if (layerDownOn) m |= Evento.LAYER_DOWN.bit();
        if (magma) m |= Evento.MAGMA.bit();
        if (pvpOn) m |= Evento.PVP.bit();
        if (hideHealth) m |= Evento.HIDE_HEALTH.bit();
        if (creepers) m |= Evento.CREEPERS.bit();
        return m;
    }

    private static SyncPayload buildPayload(MinecraftServer server) {
        List<SyncPayload.Entry> list = new ArrayList<>();
        if (pvpOn) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                list.add(new SyncPayload.Entry(p.getUuid(), p.getName().getString(), kills.getOrDefault(p.getUuid(), 0)));
            }
            list.sort(Comparator.comparingInt(SyncPayload.Entry::kills).reversed()
                    .thenComparing(SyncPayload.Entry::name));
            if (list.size() > 10) list = new ArrayList<>(list.subList(0, 10));
        }
        return new SyncPayload(mask(), list);
    }

    private static void syncAll(MinecraftServer server) {
        SyncPayload payload = buildPayload(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    private static void sync(ServerPlayerEntity p, MinecraftServer server) {
        ServerPlayNetworking.send(p, buildPayload(server));
        if (pvpOn && bar != null) bar.addPlayer(p);
    }
}
