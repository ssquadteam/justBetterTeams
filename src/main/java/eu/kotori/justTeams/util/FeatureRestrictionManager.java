package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class FeatureRestrictionManager {
    private final JustTeams plugin;

    public FeatureRestrictionManager(JustTeams plugin) {
        this.plugin = plugin;
    }

    public boolean isFeatureAllowed(Player player, String feature) {
        return !this.plugin.getConfigManager().isFeatureDisabledInWorld(feature, player.getWorld().getName());
    }

    public boolean canAffordAndPay(Player player, String feature) {
        List<String> itemCosts;
        double cost;
        if (this.plugin == null || this.plugin.getConfigManager() == null) {
            return true;
        }
        if (!this.plugin.getConfigManager().isFeatureCostsEnabled()) {
            return true;
        }
        if (this.plugin.getConfigManager().isEconomyCostsEnabled() && (cost = this.plugin.getConfigManager().getFeatureEconomyCost(feature)) > 0.0) {
            Economy economy = this.plugin.getEconomy();
            if (economy == null) {
                this.plugin.getLogger().warning("Economy cost configured but Vault not found!");
                return true;
            }
            if (!economy.has((OfflinePlayer)player, cost)) {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "insufficient_funds", new TagResolver[]{Placeholder.unparsed((String)"cost", (String)economy.format(cost)), Placeholder.unparsed((String)"balance", (String)economy.format(economy.getBalance((OfflinePlayer)player)))});
                return false;
            }
            if (!economy.withdrawPlayer((OfflinePlayer)player, cost).transactionSuccess()) {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "economy_error", new TagResolver[0]);
                return false;
            }
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "economy_charged", new TagResolver[]{Placeholder.unparsed((String)"cost", (String)economy.format(cost)), Placeholder.unparsed((String)"feature", (String)feature)});
        }
        if (this.plugin.getConfigManager().isItemCostsEnabled() && !(itemCosts = this.plugin.getConfigManager().getFeatureItemCosts(feature)).isEmpty()) {
            List<ItemStack> requiredItems = this.parseItemCosts(itemCosts);
            if (!this.hasRequiredItems(player, requiredItems)) {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "insufficient_items", new TagResolver[]{Placeholder.unparsed((String)"required", (String)this.formatRequiredItems(requiredItems)), Placeholder.unparsed((String)"current", (String)"0")});
                return false;
            }
            if (this.plugin.getConfigManager().shouldConsumeItemsOnUse()) {
                this.consumeItems(player, requiredItems);
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "items_taken", new TagResolver[]{Placeholder.unparsed((String)"items", (String)this.formatRequiredItems(requiredItems)), Placeholder.unparsed((String)"feature", (String)feature)});
            }
        }
        return true;
    }

    private List<ItemStack> parseItemCosts(List<String> itemCosts) {
        ArrayList<ItemStack> items = new ArrayList<ItemStack>();
        for (String itemCost : itemCosts) {
            String[] parts = itemCost.split(":");
            if (parts.length != 2) {
                this.plugin.getLogger().warning("Invalid item cost format: " + itemCost + " (expected MATERIAL:AMOUNT)");
                continue;
            }
            try {
                Material material = Material.valueOf((String)parts[0].toUpperCase());
                int amount = Integer.parseInt(parts[1]);
                items.add(new ItemStack(material, amount));
            } catch (IllegalArgumentException e) {
                this.plugin.getLogger().warning("Invalid material or amount in item cost: " + itemCost);
            }
        }
        return items;
    }

    private boolean hasRequiredItems(Player player, List<ItemStack> requiredItems) {
        for (ItemStack required : requiredItems) {
            int playerAmount = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || item.getType() != required.getType()) continue;
                playerAmount += item.getAmount();
            }
            if (playerAmount >= required.getAmount()) continue;
            return false;
        }
        return true;
    }

    private void consumeItems(Player player, List<ItemStack> requiredItems) {
        for (ItemStack required : requiredItems) {
            int remaining = required.getAmount();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || item.getType() != required.getType() || remaining <= 0) continue;
                int toRemove = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - toRemove);
                remaining -= toRemove;
                if (item.getAmount() > 0) continue;
                player.getInventory().remove(item);
            }
        }
        player.updateInventory();
    }

    private String formatRequiredItems(List<ItemStack> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); ++i) {
            ItemStack item = items.get(i);
            sb.append(item.getAmount()).append("x ").append(this.formatMaterialName(item.getType()));
            if (i >= items.size() - 1) continue;
            sb.append(", ");
        }
        return sb.toString();
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public String getFeatureCostInfo(String feature) {
        List<String> itemCosts;
        Economy economy;
        double cost;
        if (!this.plugin.getConfigManager().isFeatureCostsEnabled()) {
            return "";
        }
        StringBuilder info = new StringBuilder();
        if (this.plugin.getConfigManager().isEconomyCostsEnabled() && (cost = this.plugin.getConfigManager().getFeatureEconomyCost(feature)) > 0.0 && (economy = this.plugin.getEconomy()) != null) {
            info.append("Cost: ").append(economy.format(cost));
        }
        if (this.plugin.getConfigManager().isItemCostsEnabled() && !(itemCosts = this.plugin.getConfigManager().getFeatureItemCosts(feature)).isEmpty()) {
            List<ItemStack> requiredItems = this.parseItemCosts(itemCosts);
            if (info.length() > 0) {
                info.append(" + ");
            }
            info.append("Items: ").append(this.formatRequiredItems(requiredItems));
        }
        return info.toString();
    }
}

