package com;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class usmcsky extends JavaPlugin implements Listener {

    private static final int BACKPACK_SIZE = 54;
    private static final String BACKPACK_TITLE = ChatColor.DARK_GREEN + "Backpack";
    private static final String BACKPACKS_PATH = "backpacks";

    private final Map<UUID, Inventory> openBackpacks = new HashMap<>();

    private NamespacedKey backpackItemKey;
    private NamespacedKey backpackRecipeKey;
    private File backpacksFile;
    private YamlConfiguration backpacksConfig;

    @Override
    public void onEnable() {
        backpackItemKey = new NamespacedKey(this, "backpack_item");
        backpackRecipeKey = new NamespacedKey(this, "backpack");

        loadBackpacksConfig();
        registerBackpackRecipe();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (Map.Entry<UUID, Inventory> entry : openBackpacks.entrySet()) {
            saveBackpack(entry.getKey(), entry.getValue());
        }

        openBackpacks.clear();
        saveBackpacksConfig();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBackpackUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!isBackpackItem(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        openBackpack(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBackpackClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackHolder holder)) {
            return;
        }

        saveBackpack(holder.ownerId(), event.getInventory());
        openBackpacks.remove(holder.ownerId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBackpackClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackHolder)) {
            return;
        }

        Inventory backpack = event.getView().getTopInventory();
        Inventory clickedInventory = event.getClickedInventory();

        if (clickedInventory == null) {
            return;
        }

        if (event.getRawSlot() < backpack.getSize() && isBackpackItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        if (event.isShiftClick()
                && clickedInventory.equals(event.getWhoClicked().getInventory())
                && isBackpackItem(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }

        if (clickedInventory.equals(backpack)
                && event.getClick() == ClickType.NUMBER_KEY
                && isBackpackItem(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBackpackDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackHolder)) {
            return;
        }

        if (!isBackpackItem(event.getOldCursor())) {
            return;
        }

        int backpackSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < backpackSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void openBackpack(Player player) {
        Inventory backpack = openBackpacks.computeIfAbsent(player.getUniqueId(), this::loadBackpack);
        player.openInventory(backpack);
    }

    private Inventory loadBackpack(UUID playerId) {
        BackpackHolder holder = new BackpackHolder(playerId);
        Inventory inventory = Bukkit.createInventory(holder, BACKPACK_SIZE, BACKPACK_TITLE);
        holder.setInventory(inventory);

        ConfigurationSection contents = backpacksConfig.getConfigurationSection(
                BACKPACKS_PATH + "." + playerId + ".contents");
        if (contents == null) {
            return inventory;
        }

        for (String key : contents.getKeys(false)) {
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                getLogger().warning("Skipping invalid backpack slot '" + key + "' for player " + playerId + ".");
                continue;
            }

            if (slot < 0 || slot >= BACKPACK_SIZE) {
                getLogger().warning("Skipping out-of-range backpack slot '" + key + "' for player " + playerId + ".");
                continue;
            }

            ItemStack item = contents.getItemStack(key);
            if (item != null && item.getType() != Material.AIR) {
                inventory.setItem(slot, item);
            }
        }

        return inventory;
    }

    private void saveBackpack(UUID playerId, Inventory inventory) {
        String path = BACKPACKS_PATH + "." + playerId;

        backpacksConfig.set(path + ".size", BACKPACK_SIZE);
        backpacksConfig.set(path + ".contents", null);

        for (int slot = 0; slot < BACKPACK_SIZE; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            backpacksConfig.set(path + ".contents." + slot, item);
        }

        saveBackpacksConfig();
    }

    private void loadBackpacksConfig() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder at " + getDataFolder());
        }

        backpacksFile = new File(getDataFolder(), "backpacks.yml");
        if (!backpacksFile.exists()) {
            try {
                if (!backpacksFile.createNewFile()) {
                    throw new IllegalStateException("Could not create backpack data file at " + backpacksFile);
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Could not create backpack data file at " + backpacksFile, ex);
            }
        }

        backpacksConfig = YamlConfiguration.loadConfiguration(backpacksFile);
    }

    private void saveBackpacksConfig() {
        try {
            backpacksConfig.save(backpacksFile);
        } catch (IOException ex) {
            getLogger().severe("Could not save backpack data to " + backpacksFile + ": " + ex.getMessage());
        }
    }

    private void registerBackpackRecipe() {
        ItemStack backpackItem = createBackpackItem();

        ShapedRecipe recipe = new ShapedRecipe(backpackRecipeKey, backpackItem);
        recipe.shape("ILI", "LCL", "ILI");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('L', Material.LEATHER);
        recipe.setIngredient('C', Material.CHEST);

        getServer().removeRecipe(backpackRecipeKey);
        getServer().addRecipe(recipe);
    }

    private ItemStack createBackpackItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Could not create backpack item metadata.");
        }

        meta.setDisplayName(ChatColor.GOLD + "Backpack");
        meta.setLore(java.util.List.of(
                ChatColor.GRAY + "Right-click to open.",
                ChatColor.DARK_GRAY + "54 slots"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(backpackItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isBackpackItem(ItemStack item) {
        if (item == null || item.getType() != Material.CHEST) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        Byte marker = meta.getPersistentDataContainer().get(backpackItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private static final class BackpackHolder implements InventoryHolder {
        private final UUID ownerId;
        private Inventory inventory;

        private BackpackHolder(UUID ownerId) {
            this.ownerId = ownerId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private UUID ownerId() {
            return ownerId;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }
}
