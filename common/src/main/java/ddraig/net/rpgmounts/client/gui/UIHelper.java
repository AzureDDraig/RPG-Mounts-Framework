package ddraig.net.rpgmounts.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * RPG Mounts UI Helper class
 * Provides premium rendering helpers for UI panels, buttons, slots, and scrollbars.
 * Overhauled to deliver a premium, cohesive modern dark-theme visual design.
 * Integrates dynamic reflection-based color inheritance from the "RPG Waypoints and Compass" mod if installed.
 */
public class UIHelper {
    
    // Curated Design System Color Constants for Premium Dark Theme (Fallback)
    public static final int COLOR_OBSIDIAN = 0xFF12131A;
    public static final int COLOR_CHARCOAL = 0xFF1C1D26;
    public static final int COLOR_BORDER_SOFT = 0xFF2E3142;
    public static final int COLOR_GLOSS = 0x1AFFFFFF;

    // Pluggable Theme Integration via Reflection
    private static class WaypointThemeColors {
        static final boolean INSTALLED;
        static Object activeTheme;

        static {
            boolean installed = false;
            try {
                Class.forName("com.rpgwaypoints.compass.client.gui.WaypointTheme");
                installed = true;
            } catch (ClassNotFoundException e) {
                // Waypoints mod is not present
            }
            INSTALLED = installed;
        }

        static void update() {
            if (!INSTALLED) return;
            try {
                Class<?> clazz = Class.forName("com.rpgwaypoints.compass.client.gui.WaypointTheme");
                activeTheme = clazz.getField("activeTheme").get(null);
            } catch (Exception e) {
                activeTheme = null;
            }
        }

        static int getInt(String fieldName, int defaultValue) {
            if (!INSTALLED) return defaultValue;
            update();
            if (activeTheme == null) return defaultValue;
            try {
                return activeTheme.getClass().getField(fieldName).getInt(activeTheme);
            } catch (Exception e) {
                return defaultValue;
            }
        }
    }
    
    public static void drawOutline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color); // Top
        graphics.fill(x, y + height - 1, x + width, y + height, color); // Bottom
        graphics.fill(x, y, x + 1, y + height, color); // Left
        graphics.fill(x + width - 1, y, x + width, y + height, color); // Right
    }

    public static int adjustBrightness(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        if (a == 0 && color != 0) {
            a = 0xFF;
        }
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.max(0, Math.min(255, r + amount));
        g = Math.max(0, Math.min(255, g + amount));
        b = Math.max(0, Math.min(255, b + amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int blendColors(int colorA, int colorB, float ratio) {
        int aA = (colorA >> 24) & 0xFF;
        if (aA == 0 && colorA != 0) aA = 0xFF;
        int rA = (colorA >> 16) & 0xFF;
        int gA = (colorA >> 8) & 0xFF;
        int bA = colorA & 0xFF;

        int aB = (colorB >> 24) & 0xFF;
        if (aB == 0 && colorB != 0) aB = 0xFF;
        int rB = (colorB >> 16) & 0xFF;
        int gB = (colorB >> 8) & 0xFF;
        int bB = colorB & 0xFF;

        int r = (int) (rA * (1 - ratio) + rB * ratio);
        int g = (int) (gA * (1 - ratio) + gB * ratio);
        int b = (int) (bA * (1 - ratio) + bB * ratio);
        int a = (int) (aA * (1 - ratio) + aB * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void drawBeveledPanel(GuiGraphics graphics, int x, int y, int width, int height, int borderCol, int bgCol) {
        // Inherit panel colors from RPG Waypoints theme if installed
        if (WaypointThemeColors.INSTALLED) {
            bgCol = WaypointThemeColors.getInt("panelBg", bgCol);
            borderCol = WaypointThemeColors.getInt("panelBorder", borderCol);
        }

        // Soft outer drop shadow layering for a smooth, premium blurred shadow look
        graphics.fill(x + 2, y + height, x + width + 2, y + height + 2, 0x1B000000);
        graphics.fill(x + 3, y + height + 1, x + width + 1, y + height + 3, 0x0F000000);
        graphics.fill(x + width, y + 2, x + width + 2, y + height, 0x1B000000);
        graphics.fill(x + width + 1, y + 3, x + width + 3, y + height - 1, 0x0F000000);

        // Dark theme background blending - enforces high-quality dark charcoal/obsidian tones
        int obsidianBg = blendColors(COLOR_OBSIDIAN, bgCol, 0.12f);
        int bgColFrom = adjustBrightness(obsidianBg, 12);
        int bgColTo = adjustBrightness(obsidianBg, -12);
        graphics.fillGradient(x + 1, y + 1, x + width - 1, y + height - 1, bgColFrom, bgColTo);

        // Outer crisp outline border
        drawOutline(graphics, x, y, width, height, 0xFF0c0d12);

        // Core border line - colored by the theme's border color
        int primaryBorder = blendColors(COLOR_BORDER_SOFT, borderCol, 0.40f);
        drawOutline(graphics, x + 1, y + 1, width - 2, height - 2, primaryBorder);

        // 3D Inner highlights and shading
        int highlight = 0x2AFFFFFF;
        int shadow = 0x3A000000;
        // Top highlight
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, highlight);
        // Left highlight
        graphics.fill(x + 2, y + 2, x + 3, y + height - 2, highlight);
        // Bottom shadow
        graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, shadow);
        // Right shadow
        graphics.fill(x + width - 3, y + 2, x + width - 2, y + height - 2, shadow);

        // Innermost border line for high contrast
        drawOutline(graphics, x + 3, y + 3, width - 6, height - 6, 0xFF08090d);

        // Top glossy header bar accent
        graphics.fill(x + 4, y + 4, x + width - 4, y + 6, COLOR_GLOSS);
    }

    public static void drawRecessedSlot(GuiGraphics graphics, int x, int y, int width, int height, int borderCol, int bgCol) {
        // Inherit slot colors from RPG Waypoints theme if installed
        if (WaypointThemeColors.INSTALLED) {
            bgCol = WaypointThemeColors.getInt("slotBg", bgCol);
        }

        // Outer dark slot boundary outline
        drawOutline(graphics, x, y, width, height, 0xFF0b0c10);

        // Deep recessed slot background gradient (significantly darkened for indentation/sunken depth)
        int slotBg = blendColors(0xFF090a0f, bgCol, 0.08f);
        int bgColFrom = adjustBrightness(slotBg, -14);
        int bgColTo = adjustBrightness(slotBg, -4);
        graphics.fillGradient(x + 1, y + 1, x + width - 1, y + height - 1, bgColFrom, bgColTo);

        // Inner shadow simulation for realism (ambient occlusion)
        int shadowHeavy = 0x90000000;
        int shadowLight = 0x48000000;
        int softHighlight = 0x1AFFFFFF;

        // Top edge shadow layers
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, shadowHeavy);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, shadowLight);
        // Left edge shadow layers
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, shadowHeavy);
        graphics.fill(x + 2, y + 2, x + 3, y + height - 2, shadowLight);

        // Bottom and right subtle reflective highlights
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, softHighlight);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, softHighlight);
    }

    public static void drawShadedButton(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered, int baseColor) {
        // Inherit button colors from RPG Waypoints theme if installed
        if (WaypointThemeColors.INSTALLED) {
            baseColor = WaypointThemeColors.getInt("buttonBg", baseColor);
        }

        // Clean obsidian base blended with the button's accent color
        int baseButtonBg = blendColors(COLOR_CHARCOAL, baseColor, 0.22f);
        
        // Dynamic outline color: Glow gold/accent on hover, soft border when idle
        int borderCol = hovered ? adjustBrightness(baseColor, 20) : blendColors(COLOR_BORDER_SOFT, baseColor, 0.35f);
        
        // Outer border
        drawOutline(graphics, x, y, width, height, 0xFF08090d);
        // Inner colored/glowing outline
        drawOutline(graphics, x + 1, y + 1, width - 2, height - 2, borderCol);

        // Button body gradient - lighter when hovered
        int bodyFrom = adjustBrightness(baseButtonBg, hovered ? 28 : 12);
        int bodyTo = adjustBrightness(baseButtonBg, hovered ? -2 : -16);
        graphics.fillGradient(x + 2, y + 2, x + width - 2, y + height - 2, bodyFrom, bodyTo);

        // Gloss and highlight overlays
        int glossColor = hovered ? 0x36FFFFFF : 0x1EFFFFFF;
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, glossColor);
        graphics.fill(x + 2, y + 2, x + 3, y + height - 2, glossColor);

        // Subtle bottom shadow inside the button
        int innerShadow = hovered ? 0x22000000 : 0x40000000;
        graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, innerShadow);
        graphics.fill(x + width - 3, y + 2, x + width - 2, y + height - 2, innerShadow);
    }

    public static int getRarityColor(String rarity) {
        if (rarity == null) return 0xFFCCCCCC;
        switch (rarity.toUpperCase()) {
            case "UNCOMMON": return 0xFF55FF55; // Green
            case "RARE": return 0xFF5555FF; // Blue
            case "EPIC": return 0xFFFF55FF; // Purple/Magenta
            case "LEGENDARY": return 0xFFFFAA00; // Gold/Orange
            case "COMMON":
            default: return 0xFFCCCCCC; // Light Gray
        }
    }

    public static String getRarityFormattingCode(String rarity) {
        if (rarity == null) return "§7";
        switch (rarity.toUpperCase()) {
            case "UNCOMMON": return "§a";
            case "RARE": return "§9";
            case "EPIC": return "§d";
            case "LEGENDARY": return "§6";
            case "COMMON":
            default: return "§7";
        }
    }

    public static int getCategoryColor(String category) {
        if (category == null) return 0xFFFFAA00;
        switch (category.toUpperCase()) {
            case "AQUATIC": return 0xFF55FFFF; // Cyan
            case "FLYING": return 0xFFFF55FF; // Purple
            case "GROUND":
            default: return 0xFFFFAA00; // Orange/Gold
        }
    }
}
