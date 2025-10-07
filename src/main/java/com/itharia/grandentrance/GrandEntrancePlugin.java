package com.itharia.grandentrance;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class GrandEntrancePlugin extends JavaPlugin implements Listener {

    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private boolean debug;
    private final Map<UUID, Long> recentJoins = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocal();
        getServer().getPluginManager().registerEvents(this, this);
        log("&aGrandEntrance v1.1.0 enabled.");
    }

    @Override
    public void onDisable() {
        log("&7GrandEntrance disabled.");
    }

    private void reloadLocal() {
        reloadConfig();
        debug = getConfig().getBoolean("debug", false);
    }

    private void log(String msg) {
        getLogger().info(ChatColor.stripColor(legacy.deserialize(msg).toString()));
    }
    private void dlog(String msg) { if (debug) log("&8[debug]&7 " + msg); }

    enum TriggerMode { OP_ONLY, PERMISSION_ONLY, OP_OR_PERMISSION }

    private boolean shouldTrigger(Player p) {
        String modeStr = getConfig().getString("trigger.mode", "OP_OR_PERMISSION");
        TriggerMode mode = TriggerMode.valueOf(modeStr);
        String perm = getConfig().getString("trigger.permission", "grandentrance.trigger");
        return switch (mode) {
            case OP_ONLY -> p.isOp();
            case PERMISSION_ONLY -> p.hasPermission(perm);
            case OP_OR_PERMISSION -> p.isOp() || p.hasPermission(perm);
        };
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player joiner = e.getPlayer();
        if (!shouldTrigger(joiner)) {
            dlog("Joiner " + joiner.getName() + " did not match trigger.");
            return;
        }

        int cooldown = getConfig().getInt("join_cooldown_ticks", 20);
        long now = System.currentTimeMillis();
        Long last = recentJoins.get(joiner.getUniqueId());
        if (last != null && (now - last) < (cooldown * 50L)) {
            dlog("Suppressed duplicate join event for " + joiner.getName());
            return;
        }
        recentJoins.put(joiner.getUniqueId(), now);

        boolean excludeJoiner = getConfig().getBoolean("exclude_joiner", false);
        List<Player> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (excludeJoiner && online.getUniqueId().equals(joiner.getUniqueId())) continue;
            if (online.hasPermission("grandentrance.silent")) continue;
            targets.add(online);
        }
        if (targets.isEmpty()) return;

        broadcastChat(joiner);
        showTitles(targets);
        playSounds(targets);
        spawnParticles(targets);
        doConfetti(targets);
        applyEffects(targets);
        launchFireworks(targets);
        lightning(targets);
        bossbar(targets);
        hologram(joiner);
    }

    private void broadcastChat(Player joiner) {
        String msg = getConfig().getString("messages.chat_broadcast", "&6⚔ &e%player% &6(&eOP&6) has joined. All hail!");
        if (msg == null || msg.isEmpty()) return;
        msg = msg.replace("%player%", joiner.getName());
        Bukkit.getServer().broadcast(legacy.deserialize(msg));
    }

    private void showTitles(List<Player> targets) {
        if (!getConfig().getBoolean("title.enabled", true)) return;
        String main = getConfig().getString("title.main");
        String sub = getConfig().getString("title.sub");
        int fi = getConfig().getInt("title.fade_in", 10);
        int stay = getConfig().getInt("title.stay", 60);
        int fo = getConfig().getInt("title.fade_out", 20);
        Title title = Title.title(
                legacy.deserialize(main),
                legacy.deserialize(sub),
                Title.Times.times(Duration.ofMillis(fi * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fo * 50L))
        );
        for (Player t : targets) t.showTitle(title);
    }

    private void playSounds(List<Player> targets) {
        if (!getConfig().getBoolean("sounds.enabled", true)) return;
        List<Map<?, ?>> list = getConfig().getMapList("sounds.list");
        for (Map<?, ?> m : list) {
            String name = String.valueOf(m.getOrDefault("name", "ITEM_GOAT_HORN_SOUND_0"));
            float vol = ((Number) m.getOrDefault("volume", 10.0)).floatValue();
            float pitch = ((Number) m.getOrDefault("pitch", 1.0)).floatValue();
            Sound s;
            try { s = Sound.valueOf(name); }
            catch (IllegalArgumentException ex) { dlog("Invalid sound " + name); continue; }
            for (Player t : targets) t.playSound(t.getLocation(), s, SoundCategory.PLAYERS, vol, pitch);
        }
    }

    private void spawnParticles(List<Player> targets) {
        if (!getConfig().getBoolean("particles.enabled", true)) return;
        String type = getConfig().getString("particles.type", "FIREWORKS_SPARK");
        Particle particle;
        try { particle = Particle.valueOf(type); }
        catch (IllegalArgumentException ex) { particle = Particle.FIREWORKS_SPARK; }
        int count = getConfig().getInt("particles.count", 250);
        double extra = getConfig().getDouble("particles.extra", 0.1);
        double ox = getConfig().getDouble("particles.offset.x", 1.5);
        double oy = getConfig().getDouble("particles.offset.y", 1.5);
        double oz = getConfig().getDouble("particles.offset.z", 1.5);
        for (Player t : targets) t.getWorld().spawnParticle(particle, t.getLocation().add(0, 1, 0), count, ox, oy, oz, extra);
    }

    private void doConfetti(List<Player> targets) {
        if (!getConfig().getBoolean("confetti.enabled", true)) return;
        int bursts = getConfig().getInt("confetti.bursts", 3);
        int per = getConfig().getInt("confetti.per_burst", 200);
        List<Color> colors = parseColors(getConfig().getStringList("confetti.colors"));
        for (int i = 0; i < bursts; i++) {
            final int delay = i * 5;
            new BukkitRunnable() {
                @Override public void run() {
                    for (Player t : targets) spawnDustBurst(t.getLocation().add(0, 1, 0), per, colors);
                }
            }.runTaskLater(this, delay);
        }
    }

    private void spawnDustBurst(Location loc, int count, List<Color> colors) {
        World w = loc.getWorld(); if (w == null) return;
        for (int i = 0; i < count; i++) {
            Color c = colors.get((int) (Math.random() * colors.size()));
            Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.fromRGB(c.getRed(), c.getGreen(), c.getBlue()), 1.5f);
            double rx = (Math.random() - 0.5) * 2.5;
            double ry = Math.random() * 2.0;
            double rz = (Math.random() - 0.5) * 2.5;
            w.spawnParticle(Particle.REDSTONE, loc.clone().add(rx, ry, rz), 1, 0, 0, 0, 0, dust);
        }
    }

    private void applyEffects(List<Player> targets) {
        if (!getConfig().getBoolean("effects.enabled", true)) return;
        List<Map<?, ?>> list = getConfig().getMapList("effects.list");
        for (Player t : targets) {
            for (Map<?, ?> m : list) {
                String name = String.valueOf(m.getOrDefault("name", "GLOWING"));
                int dur = ((Number) m.getOrDefault("duration_ticks", 100)).intValue();
                int amp = ((Number) m.getOrDefault("amplifier", 0)).intValue();
                PotionEffectType type = PotionEffectType.getByName(name);
                if (type == null) continue;
                t.addPotionEffect(new PotionEffect(type, dur, amp, true, false, true));
            }
        }
    }

    private void launchFireworks(List<Player> targets) {
        if (!getConfig().getBoolean("fireworks.enabled", true)) return;
        int per = getConfig().getInt("fireworks.per_player", 4);
        int power = getConfig().getInt("fireworks.power", 2);
        List<org.bukkit.Color> colors = parseBukkitColors(getConfig().getStringList("fireworks.colors"));
        List<org.bukkit.Color> fades = parseBukkitColors(getConfig().getStringList("fireworks.fade_colors"));
        boolean flicker = getConfig().getBoolean("fireworks.flicker", true);
        boolean trail = getConfig().getBoolean("fireworks.trail", true);
        for (Player t : targets) {
            for (int i = 0; i < per; i++) {
                final int delay = i * 6;
                new BukkitRunnable() {
                    @Override public void run() {
                        spawnFirework(t.getLocation().clone().add(randOffset(), 0.2, randOffset()),
                                power, colors, fades, flicker, trail);
                    }
                }.runTaskLater(this, delay);
            }
        }
    }

    private void lightning(List<Player> targets) {
        if (!getConfig().getBoolean("lightning.enabled", true)) return;
        int bolts = getConfig().getInt("lightning.bolts_per_player", 2);
        double r = getConfig().getDouble("lightning.radius", 3.0);
        for (Player t : targets) {
            for (int i = 0; i < bolts; i++) {
                final int delay = i * 4;
                new BukkitRunnable() {
                    @Override public void run() {
                        Location base = t.getLocation();
                        double ang = Math.random() * Math.PI * 2;
                        double dist = Math.random() * r;
                        Location where = base.clone().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
                        base.getWorld().strikeLightningEffect(where);
                        t.playSound(where, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, 3.0f, 1.0f);
                    }
                }.runTaskLater(this, delay);
            }
        }
    }

    private void bossbar(List<Player> targets) {
        if (!getConfig().getBoolean("bossbar.enabled", true)) return;
        String text = getConfig().getString("bossbar.text", "&6👑 The Overlord Walks Among You");
        String color = getConfig().getString("bossbar.color", "PURPLE");
        String style = getConfig().getString("bossbar.style", "NOTCHED_20");
        int seconds = getConfig().getInt("bossbar.seconds", 4);

        BossBar.Color c; BossBar.Overlay o;
        try { c = BossBar.Color.valueOf(color); } catch (Exception e) { c = BossBar.Color.PURPLE; }
        try { o = BossBar.Overlay.valueOf(style); } catch (Exception e) { o = BossBar.Overlay.NOTCHED_20; }

        BossBar bar = BossBar.bossBar(legacy.deserialize(text), 1.0f, c, o);
        for (Player t : targets) t.showBossBar(bar);

        // Hide after N seconds
        new BukkitRunnable() {
            @Override public void run() { for (Player t : targets) t.hideBossBar(bar); }
        }.runTaskLater(this, seconds * 20L);
    }

    private void hologram(Player joiner) {
        if (!getConfig().getBoolean("hologram.enabled", true)) return;
        String text = getConfig().getString("hologram.text", "&6All hail &e%player%&6!");
        int seconds = getConfig().getInt("hologram.seconds", 4);
        text = text.replace("%player%", joiner.getName());

        Location loc = joiner.getLocation().clone().add(0, 2.1, 0);
        World w = loc.getWorld(); if (w == null) return;
        ArmorStand as = (ArmorStand) w.spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setInvisible(true);
        as.setMarker(true);
        as.setGravity(false);
        as.customName(legacy.deserialize(text));
        as.setCustomNameVisible(true);

        new BukkitRunnable() {
            @Override public void run() { as.remove(); }
        }.runTaskLater(this, seconds * 20L);
    }

    private double randOffset() { return (Math.random() - 0.5) * 2.5; }

    private List<org.bukkit.Color> parseBukkitColors(List<String> list) {
        List<org.bukkit.Color> out = new ArrayList<>();
        for (String s : list) {
            org.bukkit.Color c = parseHexOrNamed(s);
            if (c != null) out.add(c);
        }
        if (out.isEmpty()) out.add(org.bukkit.Color.WHITE);
        return out;
    }

    private List<java.awt.Color> parseColors(List<String> list) {
        List<java.awt.Color> out = new ArrayList<>();
        for (String s : list) {
            java.awt.Color c = parseAwtHex(s);
            if (c != null) out.add(c);
        }
        if (out.isEmpty()) out.add(java.awt.Color.WHITE);
        return out;
    }

    private java.awt.Color parseAwtHex(String s) {
        if (s == null) return null;
        s = s.trim();
        try {
            if (s.startsWith("#") && s.length() == 7) {
                return new java.awt.Color(
                        Integer.valueOf(s.substring(1,3), 16),
                        Integer.valueOf(s.substring(3,5), 16),
                        Integer.valueOf(s.substring(5,7), 16)
                );
            }
        } catch (Exception ignored) {}
        return null;
    }

    private org.bukkit.Color parseHexOrNamed(String s) {
        if (s == null) return null;
        s = s.trim();
        try {
            if (s.startsWith("#") && s.length() == 7) {
                int r = Integer.valueOf(s.substring(1, 3), 16);
                int g = Integer.valueOf(s.substring(3, 5), 16);
                int b = Integer.valueOf(s.substring(5, 7), 16);
                return org.bukkit.Color.fromRGB(r, g, b);
            } else {
                ChatColor cc = ChatColor.valueOf(s.toUpperCase(Locale.ROOT));
                return switch (cc) {
                    case RED -> org.bukkit.Color.RED;
                    case BLUE -> org.bukkit.Color.BLUE;
                    case AQUA -> org.bukkit.Color.AQUA;
                    case BLACK -> org.bukkit.Color.BLACK;
                    case DARK_BLUE -> org.bukkit.Color.BLUE;
                    case DARK_GREEN -> org.bukkit.Color.GREEN;
                    case DARK_AQUA -> org.bukkit.Color.AQUA;
                    case DARK_RED -> org.bukkit.Color.RED;
                    case GOLD -> org.bukkit.Color.ORANGE;
                    case GRAY, DARK_GRAY -> org.bukkit.Color.SILVER;
                    case GREEN -> org.bukkit.Color.GREEN;
                    case LIGHT_PURPLE, DARK_PURPLE -> org.bukkit.Color.PURPLE;
                    case WHITE -> org.bukkit.Color.WHITE;
                    case YELLOW -> org.bukkit.Color.YELLOW;
                    default -> org.bukkit.Color.WHITE;
                };
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void spawnFirework(Location loc, int power, List<org.bukkit.Color> colors, List<org.bukkit.Color> fades, boolean flicker, boolean trail) {
        World w = loc.getWorld(); if (w == null) return;
        Firework fw = w.spawn(loc, Firework.class, false);
        FireworkMeta meta = fw.getFireworkMeta();
        FireworkEffect.Builder b = FireworkEffect.builder()
                .flicker(flicker).trail(trail)
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(colors)
                .withFade(fades);
        meta.addEffect(b.build());
        meta.setPower(power);
        fw.setFireworkMeta(meta);
        fw.setVelocity(new Vector(0, 0.5, 0));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("grandentrance.admin")) {
            sender.sendMessage(legacy.deserialize("&cNo permission."));
            return true;
        }
        if (args.length == 0 || !"reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage(legacy.deserialize("&eUsage: &f/" + label + " reload"));
            return true;
        }
        reloadLocal();
        sender.sendMessage(legacy.deserialize("&aGrandEntrance config reloaded."));
        return true;
    }
}
