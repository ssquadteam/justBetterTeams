package eu.kotori.justTeams.team;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.team.TeamRole;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class Team
implements InventoryHolder {
    private final int id;
    private volatile String name;
    private volatile String tag;
    private volatile String description;
    private volatile UUID ownerUuid;
    private volatile Location homeLocation;
    private volatile String homeServer;
    private final AtomicBoolean pvpEnabled = new AtomicBoolean(false);
    private final AtomicBoolean isPublic = new AtomicBoolean(false);
    private final AtomicReference<Double> balance = new AtomicReference<Double>(0.0);
    private final AtomicInteger kills = new AtomicInteger(0);
    private final AtomicInteger deaths = new AtomicInteger(0);
    private final List<TeamPlayer> members;
    private Inventory enderChest;
    private final List<UUID> joinRequests;
    private final AtomicBoolean enderChestLock = new AtomicBoolean(false);
    private final List<UUID> enderChestViewers = new CopyOnWriteArrayList<UUID>();
    private volatile SortType currentSortType = SortType.JOIN_DATE;

    public Team(int id, String name, String tag, UUID ownerUuid, boolean defaultPvpStatus, boolean defaultPublicStatus) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.ownerUuid = ownerUuid;
        this.pvpEnabled.set(defaultPvpStatus);
        this.isPublic.set(defaultPublicStatus);
        this.members = new CopyOnWriteArrayList<TeamPlayer>();
        this.joinRequests = new CopyOnWriteArrayList<UUID>();
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getTag() {
        return this.tag != null ? this.tag : "";
    }

    public String getDescription() {
        return this.description != null ? this.description : "A new Team!";
    }

    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public Location getHomeLocation() {
        return this.homeLocation;
    }

    public String getHomeServer() {
        return this.homeServer;
    }

    public boolean isPvpEnabled() {
        return this.pvpEnabled.get();
    }

    public boolean isPublic() {
        return this.isPublic.get();
    }

    public double getBalance() {
        return this.balance.get();
    }

    public void setBalance(double balance) {
        this.balance.set(balance);
    }

    public void addBalance(double amount) {
        this.balance.updateAndGet(current -> current + amount);
    }

    public void removeBalance(double amount) {
        this.balance.updateAndGet(current -> current - amount);
    }

    public int getKills() {
        return this.kills.get();
    }

    public void setKills(int kills) {
        this.kills.set(kills);
    }

    public void incrementKills() {
        this.kills.incrementAndGet();
    }

    public int getDeaths() {
        return this.deaths.get();
    }

    public void setDeaths(int deaths) {
        this.deaths.set(deaths);
    }

    public void incrementDeaths() {
        this.deaths.incrementAndGet();
    }

    public List<TeamPlayer> getMembers() {
        return this.members;
    }

    public Inventory getEnderChest() {
        return this.enderChest;
    }

    public void setEnderChest(Inventory enderChest) {
        this.enderChest = enderChest;
    }

    public List<UUID> getJoinRequests() {
        return this.joinRequests;
    }

    public boolean isEnderChestLocked() {
        return this.enderChestLock.get();
    }

    public boolean tryLockEnderChest() {
        return this.enderChestLock.compareAndSet(false, true);
    }

    public void unlockEnderChest() {
        this.enderChestLock.set(false);
    }

    public List<UUID> getEnderChestViewers() {
        return this.enderChestViewers;
    }

    public void addEnderChestViewer(UUID playerUuid) {
        if (!this.enderChestViewers.contains(playerUuid)) {
            this.enderChestViewers.add(playerUuid);
        }
    }

    public void removeEnderChestViewer(UUID playerUuid) {
        this.enderChestViewers.remove(playerUuid);
    }

    public boolean hasEnderChestViewers() {
        return !this.enderChestViewers.isEmpty();
    }

    public SortType getCurrentSortType() {
        return this.currentSortType;
    }

    public void setSortType(SortType sortType) {
        this.currentSortType = sortType;
    }

    public void cycleSortType() {
        SortType currentSort = this.getCurrentSortType();
        SortType newSort = switch (currentSort.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> SortType.ALPHABETICAL;
            case 1 -> SortType.ONLINE_STATUS;
            case 2 -> SortType.JOIN_DATE;
        };
        this.setSortType(newSort);
    }

    public void addJoinRequest(UUID playerUuid) {
        if (!this.joinRequests.contains(playerUuid)) {
            this.joinRequests.add(playerUuid);
        }
    }

    public void removeJoinRequest(UUID playerUuid) {
        this.joinRequests.remove(playerUuid);
    }

    public List<TeamPlayer> getCoOwners() {
        return this.members.stream().filter(m -> m.getRole() == TeamRole.CO_OWNER).collect(Collectors.toList());
    }

    public List<TeamPlayer> getSortedMembers(SortType sortType) {
        return this.members.stream().sorted(sortType.getComparator()).collect(Collectors.toList());
    }

    public void addMember(TeamPlayer player) {
        this.members.add(player);
    }

    public void removeMember(UUID playerUuid) {
        this.members.removeIf(member -> member.getPlayerUuid().equals(playerUuid));
    }

    public boolean isMember(UUID playerUuid) {
        return this.members.stream().anyMatch(member -> member.getPlayerUuid().equals(playerUuid));
    }

    public boolean isOwner(UUID playerUuid) {
        return this.ownerUuid.equals(playerUuid);
    }

    public boolean hasElevatedPermissions(UUID playerUuid) {
        TeamPlayer member = this.getMember(playerUuid);
        if (member == null) {
            return false;
        }
        return member.getRole() == TeamRole.OWNER || member.getRole() == TeamRole.CO_OWNER;
    }

    public TeamPlayer getMember(UUID playerUuid) {
        return this.members.stream().filter(m -> m.getPlayerUuid().equals(playerUuid)).findFirst().orElse(null);
    }

    public void broadcast(String messageKey, TagResolver ... resolvers) {
        this.members.forEach(member -> {
            if (member.isOnline()) {
                JustTeams.getInstance().getMessageManager().sendMessage((CommandSender)member.getBukkitPlayer(), messageKey, resolvers);
            }
        });
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getColoredName() {
        return this.name;
    }

    public String getColoredTag() {
        return this.tag != null ? this.tag : "";
    }

    public String getPlainName() {
        return this.stripColorCodes(this.name);
    }

    public String getPlainTag() {
        return this.stripColorCodes(this.tag != null ? this.tag : "");
    }

    private String stripColorCodes(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?i)&[0-9A-FK-OR]", "").replaceAll("(?i)<#[0-9A-F]{6}>", "").replaceAll("(?i)</#[0-9A-F]{6}>", "");
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public void setHomeLocation(Location homeLocation) {
        this.homeLocation = homeLocation;
    }

    public void setHomeServer(String homeServer) {
        this.homeServer = homeServer;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled.set(pvpEnabled);
    }

    public void setPublic(boolean isPublic) {
        this.isPublic.set(isPublic);
    }

    public Inventory getInventory() {
        return this.enderChest;
    }

    public static enum SortType {
        JOIN_DATE(Comparator.comparing(TeamPlayer::getJoinDate)),
        ALPHABETICAL(Comparator.comparing(p -> {
            String name = Bukkit.getOfflinePlayer((UUID)p.getPlayerUuid()).getName();
            return name != null ? name.toLowerCase() : "";
        })),
        ONLINE_STATUS(Comparator.comparing(TeamPlayer::isOnline).reversed());

        private final Comparator<TeamPlayer> comparator;

        private SortType(Comparator<TeamPlayer> comparator) {
            this.comparator = comparator;
        }

        public Comparator<TeamPlayer> getComparator() {
            return this.comparator;
        }
    }
}

