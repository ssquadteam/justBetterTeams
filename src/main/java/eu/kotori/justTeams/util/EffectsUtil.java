package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class EffectsUtil {
    private static final JustTeams plugin = JustTeams.getInstance();

    public static void playSound(Player player, SoundType type) {
        if (!plugin.getConfigManager().isSoundsEnabled()) {
            return;
        }
        String soundName = switch (type.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> plugin.getConfigManager().getSuccessSound();
            case 1 -> plugin.getConfigManager().getErrorSound();
            case 2 -> plugin.getConfigManager().getTeleportSound();
        };
        try {
            Sound sound = Sound.valueOf((String)soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound name in config.yml: " + soundName);
        }
    }

    public static void spawnParticles(Location location, Particle particle, int count) {
        if (!plugin.getConfigManager().isParticlesEnabled() || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(particle, location, count, 0.5, 0.5, 0.5, 0.0);
    }

    public static enum SoundType {
        SUCCESS,
        ERROR,
        TELEPORT;

    }
}

