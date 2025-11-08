package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class ItemBuilder {
    private final ItemStack itemStack;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private static NamespacedKey getActionKey() {
        NamespacedKey key = JustTeams.getActionKey();
        if (key == null) {
            throw new IllegalStateException("JustTeams plugin not initialized - actionKey is null");
        }
        return key;
    }

    public ItemBuilder(Material material) {
        this.itemStack = new ItemStack(material);
    }

    public ItemBuilder(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
    }

    public ItemBuilder withName(String name) {
        ItemMeta meta = this.itemStack.getItemMeta();
        if (meta != null) {
            Component component = this.miniMessage.deserialize((Object)name).decoration(TextDecoration.ITALIC, false);
            meta.displayName(component);
            this.itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder withLore(String ... loreLines) {
        return this.withLore(Arrays.asList(loreLines));
    }

    public ItemBuilder withLore(List<String> loreLines) {
        ItemMeta meta = this.itemStack.getItemMeta();
        if (meta != null) {
            List lore = loreLines.stream().map(line -> this.miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false)).collect(Collectors.toList());
            meta.lore(lore);
            this.itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder asPlayerHead(UUID playerUuid) {
        ItemMeta itemMeta;
        if (this.itemStack.getType() == Material.PLAYER_HEAD && (itemMeta = this.itemStack.getItemMeta()) instanceof SkullMeta) {
            SkullMeta skullMeta = (SkullMeta)itemMeta;
            try {
                JustTeams plugin = JustTeams.getInstance();
                if (plugin != null && plugin.getBedrockSupport() != null && plugin.getBedrockSupport().isBedrockPlayer(playerUuid)) {
                    UUID javaUuid = plugin.getBedrockSupport().getJavaEditionUuid(playerUuid);
                    if (javaUuid != null && !javaUuid.equals(playerUuid)) {
                        skullMeta.setPlayerProfile(Bukkit.createProfile((UUID)javaUuid));
                    } else {
                        skullMeta.setPlayerProfile(Bukkit.createProfile((UUID)playerUuid));
                    }
                } else {
                    skullMeta.setPlayerProfile(Bukkit.createProfile((UUID)playerUuid));
                }
                this.itemStack.setItemMeta((ItemMeta)skullMeta);
            } catch (Exception e) {
                skullMeta.setPlayerProfile(Bukkit.createProfile((UUID)playerUuid));
                this.itemStack.setItemMeta((ItemMeta)skullMeta);
            }
        }
        return this;
    }

    public ItemBuilder asPlayerHeadBedrockCompatible(UUID playerUuid, Material bedrockFallback) {
        try {
            JustTeams plugin = JustTeams.getInstance();
            if (plugin != null && plugin.getBedrockSupport() != null && plugin.getBedrockSupport().isBedrockPlayer(playerUuid)) {
                if (bedrockFallback != null && bedrockFallback != Material.PLAYER_HEAD) {
                    ItemStack fallbackItem = new ItemStack(bedrockFallback);
                    return new ItemBuilder(fallbackItem);
                }
                return this.asPlayerHead(playerUuid);
            }
            return this.asPlayerHead(playerUuid);
        } catch (Exception e) {
            return this.asPlayerHead(playerUuid);
        }
    }

    public ItemBuilder withGlow() {
        this.itemStack.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        ItemMeta meta = this.itemStack.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
            this.itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder withAction(String action) {
        if (action == null || action.isEmpty()) {
            return this;
        }
        ItemMeta meta = this.itemStack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ItemBuilder.getActionKey(), PersistentDataType.STRING, (Object)action);
            this.itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder withData(String key, String value) {
        if (key == null || value == null) {
            return this;
        }
        ItemMeta meta = this.itemStack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey((Plugin)JustTeams.getInstance(), key), PersistentDataType.STRING, (Object)value);
            this.itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemStack build() {
        return this.itemStack;
    }
}

