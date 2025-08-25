/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2024 The Brewery Team
 *
 * This file is part of BreweryX.
 *
 * BreweryX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BreweryX is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BreweryX. If not, see <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package com.dre.brewery.recipe;

import com.dre.brewery.BIngredients;
import com.dre.brewery.BarrelWoodType;
import com.dre.brewery.Brew;
import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.Translatable;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.CustomItemsFile;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.configuration.sector.capsule.ConfigRecipe;
import com.dre.brewery.integration.Hook;
import com.dre.brewery.integration.PlaceholderAPIHook;
import com.dre.brewery.utility.BUtil;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.MaterialUtil;
import com.dre.brewery.utility.StringParser;
import com.dre.brewery.utility.Tuple;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A Recipe used to Brew a Brewery Potion.
 */
@Getter
@Setter
public class BRecipe implements Cloneable {

    @Getter
    private static final List<BRecipe> recipes = new ArrayList<>();
    @Getter @Setter
    public static int numConfigRecipes; // The number of recipes in the list that are from config

    // info
    private String[] name;
    private boolean saveInData; // If this recipe should be saved in data and loaded again when the server restarts. Applicable to non-config recipes
    private String id; // ID that might be given by the config

    // brewing
    private List<RecipeItem> ingredients = new ArrayList<>(); // Items and amounts
    private int difficulty; // difficulty to brew the potion, how exact the instruction has to be followed
    private int cookingTime; // time to cook in cauldron
    private byte distillruns; // runs through the brewer
    private int distillTime; // time for one distill run in seconds
    private List<BarrelWoodType> barrelTypes = new ArrayList<>(); // barrel types the brew should be aged in
    private int age; // time in minecraft days for the potions to age in barrels

    // outcome
    private PotionColor color; // color of the distilled/finished potion
    private int alcohol; // Alcohol in perfect potion
    private List<Tuple<Integer, String>> lore; // Custom Lore on the Potion. The int is for Quality Lore, 0 = any, 1,2,3 = Bad,Middle,Good
    private int[] cmData; // Custom Model Data[3] for each quality

    // drinking
    private List<BEffect> effects = new ArrayList<>(); // Special Effects when drinking
    private @Nullable List<Tuple<Integer, String>> playercmds; // Commands executed as the player when drinking
    private @Nullable List<Tuple<Integer, String>> servercmds; // Commands executed as the server when drinking
    private String drinkMsg; // Message when drinking
    private String drinkTitle; // Title to show when drinking
    private boolean glint; // If the potion should have a glint effect

    public BRecipe() {
    }

    /**
     * New BRecipe with Name.
     * <p>Use new BRecipe.Builder() for easier Recipe Creation
     *
     * @param name The name for all qualities
     */
    public BRecipe(String name, @NotNull PotionColor color) {
        this.name = new String[]{ name };
        this.color = color;
        difficulty = 5;
    }

    /**
     * New BRecipe with Names.
     * <p>Use new BRecipe.Builder() for easier Recipe Creation
     *
     * @param names {name bad, name normal, name good}
     */
    public BRecipe(String[] names, @NotNull PotionColor color) {
        this.name = names;
        this.color = color;
        difficulty = 5;
    }

    @Nullable
    public static BRecipe fromConfig(String recipeId, ConfigRecipe configRecipe) {
        BRecipe recipe = new BRecipe();
        recipe.id = recipeId;
        String nameList = configRecipe.getName();
        if (nameList != null) {
            String[] name = nameList.split("/");
            if (name.length > 2) {
                recipe.name = name;
            } else {
                recipe.name = new String[1];
                recipe.name[0] = name[0];
            }
        } else {
            Logging.errorLog(recipeId + ": Recipe Name missing or invalid!");
            return null;
        }
        if (recipe.getRecipeName() == null || recipe.getRecipeName().isEmpty()) {
            Logging.errorLog(recipeId + ": Recipe Name invalid");
            return null;
        }

        recipe.ingredients = loadIngredients(configRecipe.getIngredients(), recipeId);
        if (recipe.ingredients == null || recipe.ingredients.isEmpty()) {
            Logging.errorLog("No ingredients for: " + recipe.getRecipeName());
            return null;
        }
        recipe.cookingTime = configRecipe.getCookingTime() != null ? configRecipe.getCookingTime() : 0;
        int dis = configRecipe.getDistillRuns() != null ? configRecipe.getDistillRuns() : 0;
        if (dis > Byte.MAX_VALUE) {
            recipe.distillruns = Byte.MAX_VALUE;
        } else {
            recipe.distillruns = (byte) dis;
        }
        recipe.distillTime = (configRecipe.getDistillTime() != null ? configRecipe.getDistillTime() : 0) * 20;
        recipe.setBarrelTypes(BarrelWoodType.listFromAny(configRecipe.getWood()));
        recipe.age = configRecipe.getAge() != null ? configRecipe.getAge() : 0;
        recipe.difficulty = configRecipe.getDifficulty() != null ? configRecipe.getDifficulty() : 0;
        recipe.alcohol = configRecipe.getAlcohol() != null ? configRecipe.getAlcohol() : 0;

        String col = configRecipe.getColor() != null ? configRecipe.getColor() : "BLUE";
        recipe.color = PotionColor.fromString(col);
        if (recipe.color == PotionColor.WATER && !col.equals("WATER")) {
            Logging.errorLog("Invalid Color '" + col + "' in Recipe: " + recipe.getRecipeName());
            return null;
        }

        recipe.lore = loadQualityStringList(BUtil.getListSafely(configRecipe.getLore()), StringParser.ParseType.LORE);

        recipe.servercmds = loadQualityStringList(configRecipe.getServerCommands(), StringParser.ParseType.CMD);
        recipe.playercmds = loadQualityStringList(configRecipe.getPlayerCommands(), StringParser.ParseType.CMD);

        recipe.drinkMsg = BUtil.color(configRecipe.getDrinkMessage());
        recipe.drinkTitle = BUtil.color(configRecipe.getDrinkTitle());
        recipe.glint = configRecipe.getGlint() != null ? configRecipe.getGlint() : false;

        if (configRecipe.getCustomModelData() != null) {
            String[] cmdParts = configRecipe.getCustomModelData().split("/");
            int[] cmData = new int[3];
            for (int i = 0; i < 3; i++) {
                if (cmdParts.length > i) {
                    cmData[i] = BUtil.getRandomIntInRange(cmdParts[i]);
                } else {
                    cmData[i] = i == 0 ? 0 : cmData[i - 1];
                }
            }
            recipe.cmData = cmData;
        }

        List<String> effectStringList = configRecipe.getEffects() != null ? configRecipe.getEffects() : Collections.emptyList();
        for (String effectString : effectStringList) {
            BEffect effect = new BEffect(effectString);
            if (effect.isValid()) {
                recipe.effects.add(effect);
            } else {
                Logging.errorLog("Error adding Effect to Recipe: " + recipe.getRecipeName());
            }
        }
        return recipe;
    }

    public static List<RecipeItem> loadIngredients(ConfigurationSection cfg, String recipeId) {
        List<String> ingredientsList;
        if (cfg.isString(recipeId + ".ingredients")) {
            ingredientsList = new ArrayList<>(1);
            ingredientsList.add(cfg.getString(recipeId + ".ingredients", "x"));
        } else {
            ingredientsList = cfg.getStringList(recipeId + ".ingredients");
        }
        return loadIngredients(ingredientsList, recipeId);
    }

    public static List<RecipeItem> loadIngredients(List<String> stringList, String recipeId) {
        if (stringList == null) {
            stringList = Collections.emptyList();
        }
        List<RecipeItem> ingredients = new ArrayList<>();
        for (String s : stringList) {
            IngredientResult result = loadIngredientVerbose(s);
            if (result instanceof IngredientResult.Success success) {
                ingredients.add(success.ingredient);
            } else {
                IngredientResult.Error error = (IngredientResult.Error) result;
                Lang lang = ConfigManager.getConfig(Lang.class);
                String errorMessage = lang.getEntry(error.error().getTranslationKey(), error.invalidPart());
                Logging.errorLog(recipeId + ": " + errorMessage);
                return null;
            }
        }
        return ingredients;
    }

    public static IngredientResult loadIngredientVerbose(String item) {
        String[] ingredParts = item.split("/");
        int amount = 1;
        if (ingredParts.length == 2) {
            amount = BUtil.getRandomIntInRange(ingredParts[1]);
            if (amount < 1) {
                return new IngredientResult.Error(IngredientError.INVALID_AMOUNT, ingredParts[1]);
            }
        }
        String[] matParts;
        if (ingredParts[0].contains(",")) {
            matParts = ingredParts[0].split(",");
        } else if (ingredParts[0].contains(";")) {
            matParts = ingredParts[0].split(";");
        } else {
            matParts = ingredParts[0].split("\\.");
        }


        // Check if this is a Plugin Item
        String[] pluginItem = matParts[0].split(":");
        if (pluginItem.length > 1) {
            StringBuilder itemId = new StringBuilder();
            for (int i = 1; i < pluginItem.length; i++) { // Append all but the first part to include namespaces.
                itemId.append(pluginItem[i]);
            }
            RecipeItem custom = PluginItem.fromConfig(pluginItem[0], itemId.toString());
            if (custom != null) {
                custom.setAmount(amount);
                custom.makeImmutable();
                BCauldronRecipe.acceptedCustom.add(custom);
                return new IngredientResult.Success(custom);
            } else {
                // TODO Maybe load later ie on first use of recipe?
                return new IngredientResult.Error(IngredientError.INVALID_PLUGIN_ITEM, item);
            }
        }

        // Try to find this Ingredient as Custom Item
        for (RecipeItem custom : ConfigManager.getConfig(CustomItemsFile.class).getRecipeItems().stream().filter(Objects::nonNull).toList()) {
            if (custom.getConfigId().equalsIgnoreCase(matParts[0])) {
                custom = custom.getMutableCopy();
                custom.setAmount(amount);
                custom.makeImmutable();
                if (custom.hasMaterials()) {
                    BCauldronRecipe.acceptedMaterials.addAll(custom.getMaterials());
                }
                // Add it as acceptedCustom
                if (!BCauldronRecipe.acceptedCustom.contains(custom)) {
                    BCauldronRecipe.acceptedCustom.add(custom);
                }
                return new IngredientResult.Success(custom);
            }
        }

        Material mat = MaterialUtil.getMaterialSafely(matParts[0]);
        short durability = -1;
        if (matParts.length == 2) {
            durability = (short) BUtil.getRandomIntInRange(matParts[1]);
        }
        if (mat == null && Hook.VAULT.isEnabled()) {
            try {
                net.milkbowl.vault.item.ItemInfo vaultItem = net.milkbowl.vault.item.Items.itemByString(matParts[0]);
                if (vaultItem != null) {
                    mat = vaultItem.getType();
                    if (durability == -1 && vaultItem.getSubTypeId() != 0) {
                        durability = vaultItem.getSubTypeId();
                    }
                    if (mat.name().contains("LEAVES")) {
                        if (durability > 3) {
                            durability -= 4; // Vault has leaves with higher durability
                        }
                    }
                }
            } catch (Throwable e) {
                Logging.errorLog("Could not check vault for Item Name", e);
            }
        }
        if (mat != null) {
            RecipeItem rItem;
            if (durability > -1) {
                rItem = new SimpleItem(mat, durability);
            } else {
                rItem = new SimpleItem(mat);
            }
            rItem.setAmount(amount);
            rItem.makeImmutable();
            BCauldronRecipe.acceptedMaterials.add(mat);
            BCauldronRecipe.acceptedSimple.add(mat);
            return new IngredientResult.Success(rItem);
        } else {
            return new IngredientResult.Error(IngredientError.INVALID_MATERIAL, ingredParts[0]);
        }
    }

    public sealed interface IngredientResult {
        record Success(RecipeItem ingredient) implements IngredientResult {}
        record Error(IngredientError error, String invalidPart) implements IngredientResult {}
    }

    @AllArgsConstructor
    @Getter
    public enum IngredientError implements Translatable {
        INVALID_AMOUNT("Error_InvalidAmount"),
        INVALID_PLUGIN_ITEM("Error_InvalidPluginItem"),
        INVALID_MATERIAL("Error_InvalidMaterial");

        private final String translationKey;
    }

    /**
     * Load a list of strings from a ConfigurationSection and parse the quality
     */
    @Nullable
    public static List<Tuple<Integer, String>> loadQualityStringList(ConfigurationSection cfg, String path, StringParser.ParseType parseType) {
        List<String> load = BUtil.loadCfgStringList(cfg, path);
        if (load != null) {
            return loadQualityStringList(load, parseType);
        }
        return null;
    }

    public static List<Tuple<Integer, String>> loadQualityStringList(List<String> stringList, StringParser.ParseType parseType) {
        List<Tuple<Integer, String>> result = new ArrayList<>();
        if (stringList == null) {
            return result;
        }
        for (String line : stringList) {
            result.add(StringParser.parseQuality(line, parseType));
        }
        return result;
    }

    public boolean isAlcoholic() {
        return alcohol > 0;
    }

    /**
     * check every part of the recipe for validity.
     */
    public boolean isValid() {
        if (ingredients == null || ingredients.isEmpty()) {
            Logging.errorLog("No ingredients could be loaded for Recipe: " + getRecipeName());
            return false;
        }
        if (cookingTime < 1) {
            Logging.errorLog("Invalid cooking time '" + cookingTime + "' in Recipe: " + getRecipeName());
            return false;
        }
        if (distillruns < 0) {
            Logging.errorLog("Invalid distillruns '" + distillruns + "' in Recipe: " + getRecipeName());
            return false;
        }
        if (distillTime < 0) {
            Logging.errorLog("Invalid distilltime '" + distillTime + "' in Recipe: " + getRecipeName());
            return false;
        }
//		if (wood < 0 || wood > LegacyUtil.TOTAL_WOOD_TYPES) {
//			Logging.errorLog("Invalid wood type '" + wood + "' in Recipe: " + getRecipeName());
//			return false;
//		}
        if (age < 0) {
            Logging.errorLog("Invalid age time '" + age + "' in Recipe: " + getRecipeName());
            return false;
        }
        if (difficulty < 0 || difficulty > 10) {
            Logging.errorLog("Invalid difficulty '" + difficulty + "' in Recipe: " + getRecipeName());
            return false;
        }
        return true;
    }

    /**
     * allowed deviation to the recipes count of ingredients at the given difficulty
     */
    public int allowedCountDiff(int count) {
        if (count < 8) {
            count = 8;
        }
        int allowedCountDiff = Math.round((float) ((11.0 - difficulty) * (count / 10.0)));

        if (allowedCountDiff == 0) {
            return 1;
        }
        return allowedCountDiff;
    }

    /**
     * allowed deviation to the recipes cooking-time at the given difficulty
     */
    public int allowedTimeDiff(int time) {
        if (time < 8) {
            time = 8;
        }
        int allowedTimeDiff = Math.round((float) ((11.0 - difficulty) * (time / 10.0)));

        if (allowedTimeDiff == 0) {
            return 1;
        }
        return allowedTimeDiff;
    }

    /**
     * Gets the <strong>primary</strong> barrel type out of all supported.
     * @return the barrel type
     * @see #getBarrelTypes() for the full list
     */
    public BarrelWoodType getWood() {
        return barrelTypes.isEmpty() ? BarrelWoodType.ANY : barrelTypes.get(0);
    }

    public boolean usesAnyWood() {
        return getWood() == BarrelWoodType.ANY;
    }

    public void setWood(BarrelWoodType wood) {
        barrelTypes = Collections.singletonList(wood);
    }

    public void setBarrelTypes(List<BarrelWoodType> barrelTypes) {
        if (barrelTypes.stream().anyMatch(b -> b == BarrelWoodType.ANY)) {
            setWood(BarrelWoodType.ANY);
        } else {
            this.barrelTypes = barrelTypes;
        }
    }

    /**
     * difference between given and recipe-wanted woodtype
     */
    public float getWoodDiff(float wood) {
        return (float) barrelTypes.stream()
            .mapToDouble(w -> Math.abs(wood - w.getIndex()))
            .min()
            .orElse(0.0);
    }

    public boolean isCookingOnly() {
        return age == 0 && distillruns == 0;
    }

    public boolean needsDistilling() {
        return distillruns != 0;
    }

    public boolean needsToAge() {
        return age != 0;
    }

    /**
     * true if given list misses an ingredient
     */
    public boolean isMissingIngredients(List<Ingredient> list) {
        if (list.size() < ingredients.size()) {
            return true;
        }
        for (RecipeItem rItem : ingredients) {
            boolean matches = false;
            for (Ingredient used : list) {
                if (rItem.matches(used)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return true;
            }
        }
        return false;
    }
    public List<RecipeItem> getMissingIngredients(List<Ingredient> list) {
        List<RecipeItem> missing = new ArrayList<>();
        for (RecipeItem rItem : ingredients) {
            boolean matches = false;
            for (Ingredient used : list) {
                if (rItem.matches(used)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                missing.add(rItem);
            }
        }
        return missing;
    }

    public void applyDrinkFeatures(Player player, int quality) {
        List<String> playerCmdsForQuality = getPlayercmdsForQuality(quality);
        if (playerCmdsForQuality != null) {
            for (String cmd : playerCmdsForQuality) {
                scheduleCommand(player, cmd, player.getName(), quality, false);
            }
        }
        List<String> serverCmdsForQuality = getServercmdsForQuality(quality);
        if (serverCmdsForQuality != null) {
            for (String cmd : serverCmdsForQuality) {
                scheduleCommand(player, cmd, player.getName(), quality, true);
            }
        }
        if (drinkMsg != null) {
            player.sendMessage(BUtil.applyPlaceholders(drinkMsg, player.getName(), quality));
        }
        if (drinkTitle != null) {
            player.sendTitle("", BUtil.applyPlaceholders(drinkTitle, player.getName(), quality), 10, 90, 30);
        }
    }

    private void scheduleCommand(Player player, String cmd, String playerName, int quality, boolean isServerCommand) {
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.contains("/")) {
            String[] parts = cmd.split("/");
            String command = parts[0].trim(); // Needs to be effectively final for scheduling
            cmd = parts[0].trim();
            String delay = parts[1].trim();
            long delayTicks = parseDelayToTicks(delay);
            if (delayTicks > 0) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        executeCommand(player, command, playerName, quality, isServerCommand);
                    }
                }.runTaskLater(BreweryPlugin.getInstance(), delayTicks);
                return;
            }
        }
        // Execute command immediately if no delay is specified
        executeCommand(player, cmd, playerName, quality, isServerCommand);
    }

    private long parseDelayToTicks(String delay) {
        try {
            if (delay.endsWith("s")) {
                int seconds = Integer.parseInt(delay.substring(0, delay.length() - 1));
                return seconds * 20L; // 20 ticks per second
            } else if (delay.endsWith("m")) {
                int minutes = Integer.parseInt(delay.substring(0, delay.length() - 1));
                return minutes * 1200L; // 1200 ticks per minute
            }
        } catch (NumberFormatException e) {
            // Invalid format: Default to 0
        }
        return 0; // Immediately execute command
    }

    private void executeCommand(Player player, String cmd, String playerName, int quality, boolean isServerCommand) {
        String finalCommand = PlaceholderAPIHook.PLACEHOLDERAPI.setPlaceholders(player, BUtil.applyPlaceholders(cmd, playerName, quality));
        if (isServerCommand) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
        } else {
            Bukkit.dispatchCommand(player, finalCommand);
        }
    }

    /**
     * Create a Potion from this Recipe with best values.
     * Quality can be set, but will reset to 10 if unset immutable and put in a barrel
     *
     * @param quality The Quality of the Brew
     * @return The Created Item
     */
    public ItemStack create(int quality, Player player) {
        return createBrew(quality).createItem(this, player);
    }

    public ItemStack create(int quality) {
        return createBrew(quality).createItem(this);
    }

    /**
     * Create a Brew from this Recipe with best values.
     * Quality can be set, but will reset to 10 if unset immutable and put in a barrel
     *
     * @param quality The Quality of the Brew
     * @return The created Brew
     */
    public Brew createBrew(int quality) {
        List<Ingredient> list = new ArrayList<>(ingredients.size());
        for (RecipeItem rItem : ingredients) {
            Ingredient ing = rItem.toIngredientGeneric();
            ing.setAmount(rItem.getAmount());
            list.add(ing);
        }

        BIngredients bIngredients = new BIngredients(list, cookingTime);

        return new Brew(bIngredients, quality, 0, distillruns, getAge(), getWood(), getRecipeName(), false, true, 0);
    }

    public void updateAcceptedLists() {
        for (RecipeItem ingredient : getIngredients()) {
            if (ingredient.hasMaterials()) {
                BCauldronRecipe.acceptedMaterials.addAll(ingredient.getMaterials());
            }
            if (ingredient instanceof SimpleItem) {
                BCauldronRecipe.acceptedSimple.add(((SimpleItem) ingredient).getMaterial());
            } else {
                // Add it as acceptedCustom
                if (!BCauldronRecipe.acceptedCustom.contains(ingredient)) {
                    BCauldronRecipe.acceptedCustom.add(ingredient);
                }
            }
        }
    }


    // Getter

    /**
     * how many of a specific ingredient in the recipe
     */
    public int amountOf(Ingredient ing) {
        for (RecipeItem rItem : ingredients) {
            if (rItem.matches(ing)) {
                return rItem.getAmount();
            }
        }
        return 0;
    }

    /**
     * how many of a specific ingredient in the recipe
     */
    public int amountOf(ItemStack item) {
        for (RecipeItem rItem : ingredients) {
            if (rItem.matches(item)) {
                return rItem.getAmount();
            }
        }
        return 0;
    }

    /**
     * Same as getName(5)
     */
    public String getRecipeName() {
        return getName(5);
    }

    /**
     * name that fits the quality
     */
    public String getName(int quality) {
        if (name.length > 2) {
            if (quality <= 3) {
                return name[0];
            } else if (quality <= 7) {
                return name[1];
            } else {
                return name[2];
            }
        } else {
            return name[0];
        }
    }

    /**
     * If one of the quality names equalIgnoreCase given name
     */
    public boolean hasName(String name) {
        for (String test : this.name) {
            if (test.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    public PotionColor getColor() {
        return color;
    }

    public boolean hasLore() {
        return lore != null && !lore.isEmpty();
    }

    @Nullable
    public List<Tuple<Integer, String>> getLore() {
        return lore;
    }

    @Nullable
    public List<String> getLoreForQuality(int quality) {
        return getStringsForQuality(quality, lore);
    }

    @Nullable
    public List<String> getPlayercmdsForQuality(int quality) {
        return getStringsForQuality(quality, playercmds);
    }

    @Nullable
    public List<String> getServercmdsForQuality(int quality) {
        return getStringsForQuality(quality, servercmds);
    }

    /**
     * Get a quality filtered list of supported attributes
     */
    @Nullable
    public List<String> getStringsForQuality(int quality, List<Tuple<Integer, String>> source) {
        if (source == null) return null;
        int plus;
        if (quality <= 3) {
            plus = 1;
        } else if (quality <= 7) {
            plus = 2;
        } else {
            plus = 3;
        }
        List<String> list = new ArrayList<>(source.size());
        for (Tuple<Integer, String> line : source) {
            if (line.first() == 0 || line.first() == plus) {
                list.add(line.second());
            }
        }
        return list;
    }


    public void setColor(@NotNull PotionColor color) {
        this.color = color;
    }


    @Override
    public String toString() {
        return "BRecipe{" +
            "name=" + Arrays.toString(name) +
            ", ingredients=" + ingredients +
            ", difficulty=" + difficulty +
            ", cookingTime=" + cookingTime +
            ", distillruns=" + distillruns +
            ", distillTime=" + distillTime +
            ", barrelTypes=" + barrelTypes +
            ", age=" + age +
            ", color=" + color +
            ", alcohol=" + alcohol +
            ", lore=" + lore +
            ", cmData=" + Arrays.toString(cmData) +
            ", effects=" + effects +
            ", playercmds=" + playercmds +
            ", servercmds=" + servercmds +
            ", drinkMsg='" + drinkMsg + '\'' +
            ", drinkTitle='" + drinkTitle + '\'' +
            ", glint=" + glint +
            '}';
    }

    /**
     * Gets a Modifiable Sublist of the Recipes that are loaded by config.
     * <p>Changes are directly reflected by the main list of all recipes
     * <br>Changes to the main List of all recipes will make the reference to this sublist invalid
     *
     * <p>After adding or removing elements, BRecipe.numConfigRecipes MUST be updated!
     */
    public static List<BRecipe> getConfigRecipes() {
        return recipes.subList(0, numConfigRecipes);
    }

    /**
     * Gets a Modifiable Sublist of the Recipes that are added by plugins.
     * <p>Changes are directly reflected by the main list of all recipes
     * <br>Changes to the main List of all recipes will make the reference to this sublist invalid
     */
    public static List<BRecipe> getAddedRecipes() {
        return recipes.subList(numConfigRecipes, recipes.size());
    }

    /**
     * Gets the main List of all recipes.
     */
    public static List<BRecipe> getAllRecipes() {
        return recipes;
    }


    /**
     * Get the BRecipe that has the given name as one of its quality names.
     */
    @Nullable
    public static BRecipe getMatching(String name) {
        BRecipe mainNameRecipe = get(name);
        if (mainNameRecipe != null) {
            return mainNameRecipe;
        }
        for (BRecipe recipe : recipes) {
            if (recipe.getName(1).equalsIgnoreCase(name)) {
                return recipe;
            } else if (recipe.getName(10).equalsIgnoreCase(name)) {
                return recipe;
            }
        }
        for (BRecipe recipe : recipes) {
            if (name.equalsIgnoreCase(recipe.getId())) {
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    public static BRecipe getById(String id) {
        for (BRecipe recipe : recipes) {
            if (id.equals(recipe.getId())) {
                return recipe;
            }
        }
        return null;
    }


    /**
     * Get the BRecipe that has that name as its name
     */
    @Nullable
    public static BRecipe get(String name) {
        for (BRecipe recipe : recipes) {
            if (recipe.getRecipeName().equalsIgnoreCase(name)) {
                return recipe;
            }
        }
        return null;
    }

    @Override
    public BRecipe clone() {
        try {
            BRecipe clone = (BRecipe) super.clone();
            clone.name = this.name.clone();
            clone.ingredients = new ArrayList<>(this.ingredients.size());
            for (RecipeItem item : this.ingredients) {
                clone.ingredients.add(item.getMutableCopy());
            }
            clone.lore = (this.lore != null) ? new ArrayList<>(this.lore) : null;
            clone.playercmds = (this.playercmds != null) ? new ArrayList<>(this.playercmds) : null;
            clone.servercmds = (this.servercmds != null) ? new ArrayList<>(this.servercmds) : null;
            clone.effects = new ArrayList<>(this.effects.size());
            for (BEffect effect : this.effects) {
                clone.effects.add(effect.clone());
            }
            clone.cmData = (this.cmData != null) ? this.cmData.clone() : null;
            clone.drinkMsg = this.drinkMsg;
            clone.drinkTitle = this.drinkTitle;
            clone.glint = this.glint;
            clone.saveInData = this.saveInData;
            clone.id = this.id;
            clone.difficulty = this.difficulty;
            clone.cookingTime = this.cookingTime;
            clone.distillruns = this.distillruns;
            clone.distillTime = this.distillTime;
            clone.barrelTypes = this.barrelTypes;
            clone.age = this.age;
            clone.color = this.color;
            clone.alcohol = this.alcohol;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

	/*public static void saveAddedRecipes(ConfigurationSection cfg) {
		int i = 0;
		for (BRecipe recipe : getAddedRecipes()) {
			if (recipe.isSaveInData()) {
				cfg.set(i + ".name", recipe.name);
			}
		}
	}*/


    /**
     * Builder to easily create Recipes
     */
    public static class Builder {
        private BRecipe recipe;

        public Builder(String name) {
            recipe = new BRecipe(name, PotionColor.WATER);
        }

        public Builder(String... names) {
            recipe = new BRecipe(names, PotionColor.WATER);
        }


        public Builder addIngredient(RecipeItem... item) {
            Collections.addAll(recipe.ingredients, item);
            return this;
        }

        public Builder addIngredient(ItemStack... item) {
            for (ItemStack i : item) {
                CustomItem customItem = new CustomItem(i);
                customItem.setAmount(i.getAmount());
                recipe.ingredients.add(customItem);
            }
            return this;
        }

        public Builder difficulty(int difficulty) {
            recipe.difficulty = difficulty;
            return this;
        }

        public Builder color(String colorString) {
            recipe.color = PotionColor.fromString(colorString);
            return this;
        }

        public Builder color(PotionColor color) {
            recipe.color = color;
            return this;
        }

        public Builder color(Color color) {
            recipe.color = PotionColor.fromColor(color);
            return this;
        }

        public Builder cook(int cookTime) {
            recipe.cookingTime = cookTime;
            return this;
        }

        public Builder distill(byte distillRuns, int distillTime) {
            recipe.distillruns = distillRuns;
            recipe.distillTime = distillTime;
            return this;
        }

        public Builder age(int age, BarrelWoodType wood) {
            recipe.age = age;
            recipe.setWood(wood);
            return this;
        }

        public Builder age(int age, BarrelWoodType... barrelTypes) {
            recipe.age = age;
            recipe.setBarrelTypes(List.of(barrelTypes));
            return this;
        }

        public Builder alcohol(int alcohol) {
            recipe.alcohol = alcohol;
            return this;
        }

        public Builder addLore(String line) {
            return addLore(0, line);
        }

        /**
         * Add a Line of Lore
         *
         * @param quality 0 for any quality, 1: bad, 2: normal, 3: good
         * @param line    The Line for custom lore to add
         * @return this
         */
        public Builder addLore(int quality, String line) {
            if (quality < 0 || quality > 3) {
                throw new IllegalArgumentException("Lore Quality must be 0 - 3");
            }
            if (recipe.lore == null) {
                recipe.lore = new ArrayList<>();
            }
            recipe.lore.add(new Tuple<>(quality, line));
            return this;
        }

        /**
         * Add Commands that are executed by the player on drinking
         */
        public Builder addPlayerCmds(String... cmds) {
            List<Tuple<Integer, String>> playercmds = new ArrayList<>(cmds.length);

            for (String cmd : cmds) {
                playercmds.add(StringParser.parseQuality(cmd, StringParser.ParseType.CMD));
            }
            if (recipe.playercmds == null) {
                recipe.playercmds = playercmds;
            } else {
                recipe.playercmds.addAll(playercmds);
            }
            return this;
        }

        /**
         * Add Commands that are executed by the server on drinking
         */
        public Builder addServerCmds(String... cmds) {
            List<Tuple<Integer, String>> servercmds = new ArrayList<>(cmds.length);

            for (String cmd : cmds) {
                servercmds.add(StringParser.parseQuality(cmd, StringParser.ParseType.CMD));
            }
            if (recipe.servercmds == null) {
                recipe.servercmds = servercmds;
            } else {
                recipe.servercmds.addAll(servercmds);
            }
            return this;
        }

        /**
         * Add Message that is sent to the player in chat when he drinks the brew
         */
        public Builder drinkMsg(String msg) {
            recipe.drinkMsg = msg;
            return this;
        }

        /**
         * Add Message that is sent to the player as a small title when he drinks the brew
         */
        public Builder drinkTitle(String title) {
            recipe.drinkTitle = title;
            return this;
        }

        /**
         * Add a Glint to the Potion
         */
        public Builder glint(boolean glint) {
            recipe.glint = glint;
            return this;
        }

        /**
         * Set the Optional ID of this recipe
         */
        public Builder setID(String id) {
            recipe.id = id;
            return this;
        }

        /**
         * Add Custom Model Data for each Quality
         */
        public Builder addCustomModelData(int bad, int normal, int good) {
            recipe.cmData = new int[]{ bad, normal, good };
            return this;
        }

        public Builder addEffects(BEffect... effects) {
            Collections.addAll(recipe.effects, effects);
            return this;
        }

        public BRecipe get() {
            if (recipe.name == null) {
                throw new IllegalArgumentException("Recipe name is null");
            }
            if (recipe.name.length != 1 && recipe.name.length != 3) {
                throw new IllegalArgumentException("Recipe name neither 1 nor 3");
            }
            if (recipe.color == null) {
                throw new IllegalArgumentException("Recipe has no color");
            }
            if (recipe.ingredients == null || recipe.ingredients.isEmpty()) {
                throw new IllegalArgumentException("Recipe has no ingredients");
            }
            if (!recipe.isValid()) {
                throw new IllegalArgumentException("Recipe has not valid");
            }
            for (RecipeItem ingredient : recipe.ingredients) {
                ingredient.makeImmutable();
            }
            return recipe;
        }
    }
}
