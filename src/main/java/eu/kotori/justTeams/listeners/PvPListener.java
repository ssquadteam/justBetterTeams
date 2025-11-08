package eu.kotori.justTeams.listeners;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

public class PvPListener
implements Listener {
    private final TeamManager teamManager;

    public PvPListener(JustTeams plugin) {
        this.teamManager = plugin.getTeamManager();
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player victim = (Player)entity;
        Player attacker = null;
        Entity entity2 = event.getDamager();
        if (entity2 instanceof Player) {
            Player p;
            attacker = p = (Player)entity2;
        } else {
            Projectile projectile;
            ProjectileSource projectileSource;
            entity2 = event.getDamager();
            if (entity2 instanceof Projectile && (projectileSource = (projectile = (Projectile)entity2).getShooter()) instanceof Player) {
                Player p;
                attacker = p = (Player)projectileSource;
            }
        }
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        Team victimTeam = this.teamManager.getPlayerTeam(victim.getUniqueId());
        Team attackerTeam = this.teamManager.getPlayerTeam(attacker.getUniqueId());
        if (victimTeam == null || attackerTeam == null || victimTeam.getId() != attackerTeam.getId()) {
            return;
        }
        if (!victimTeam.isPvpEnabled()) {
            event.setCancelled(true);
        }
    }
}

