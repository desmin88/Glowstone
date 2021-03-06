package net.glowstone.util;

import org.bukkit.Achievement;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.UnsafeValues;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of Bukkit's internal-use UnsafeValues. In CraftBukkit, this
 * uses Mojang identifiers, but here we just stick to Bukkit's. The
 * implementation may be a bit sketchy but this isn't a problem since the
 * behavior of this class isn't strictly specified.
 */
@Deprecated
public class GlowUnsafeValues implements UnsafeValues {

    public Material getMaterialFromInternalName(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public List<String> tabCompleteInternalMaterialName(String token, List<String> completions) {
        List<String> materialNames = new ArrayList<>(Material.values().length);
        for (Material mat : Material.values()) {
            materialNames.add(mat.name());
        }
        return org.bukkit.util.StringUtil.copyPartialMatches(token, materialNames, completions);
    }

    public ItemStack modifyItemStack(ItemStack stack, String arguments) {
        return stack;
    }

    public Statistic getStatisticFromInternalName(String name) {
        try {
            return Statistic.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Achievement getAchievementFromInternalName(String name) {
        try {
            return Achievement.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public List<String> tabCompleteInternalStatisticOrAchievementName(String token, List<String> completions) {
        Statistic[] stats = Statistic.values();
        Achievement[] achievements = Achievement.values();
        List<String> names = new ArrayList<>(stats.length + achievements.length);
        for (Statistic stat : stats) {
            names.add(stat.name());
        }
        for (Achievement achievement : achievements) {
            names.add(achievement.name());
        }
        return org.bukkit.util.StringUtil.copyPartialMatches(token, names, completions);
    }
}
