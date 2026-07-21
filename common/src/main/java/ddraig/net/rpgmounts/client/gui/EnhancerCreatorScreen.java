package ddraig.net.rpgmounts.client.gui;

import ddraig.net.rpgmounts.client.integration.RPGWaypointsIntegration;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.network.ModPackets;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import ddraig.net.rpgmounts.data.MountData;
import java.util.List;

/**
 * RPG Mounts Enhancer Creator Screen
 * Admin-only utility to visually construct mount enhancers.
 * Renders double gold-bordered screen matching Waypoint UI theme.
 * 
 * Change Log:
 * - 2026-06-19: [Initial Creation] - Created screen with category, type/stat, and value adjusters, supporting standalone or in-tab rendering.
 */
public class EnhancerCreatorScreen extends Screen {
    private String activeCategory = "defense";
    private String activeType = "max_health";
    private double activeValue = 10.0;

    private static final String[] CATEGORIES = {"defense", "movement", "damage", "ability"};
    private int categoryIndex = 0;
    private java.util.List<Component> hoveredTooltip = null;

    private static final java.util.Map<String, String[]> STATS_BY_CATEGORY = java.util.Map.of(
        "defense", new String[]{"max_health", "armor", "flat_damage_reduction"},
        "movement", new String[]{"speed", "swim_speed", "fly_speed", "jump_height", "jump_strength"},
        "damage", new String[]{"damage_boost", "strength", "attack_speed"},
        "ability", new String[]{"cooldown_reduction", "stamina_cost_reduction", "grant_ability"}
    );

    private java.util.List<String> getAbilityNames() {
        java.util.List<String> list = new java.util.ArrayList<>(MountRegistry.customAbilities.keySet());
        if (list.isEmpty()) {
            list.add("Swift Dash");
            list.add("Fireball");
            list.add("Ground Slam");
            list.add("Iron Skin");
        }
        return list;
    }

    public EnhancerCreatorScreen() {
        super(Component.literal("RPG Mount Enhancer Creator"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.hoveredTooltip = null;
        if (Minecraft.getInstance().screen == this) {
            this.renderBackground(graphics);
        }

        int width = 220;
        int height = 160;
        int left = (this.width - width) / 2;
        int top = (this.height - height) / 2;

        renderInTab(graphics, mouseX, mouseY, partialTicks, left, top, width, height);

        super.render(graphics, mouseX, mouseY, partialTicks);

        if (this.hoveredTooltip != null && Minecraft.getInstance().screen == this) {
            graphics.renderComponentTooltip(this.font, this.hoveredTooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int width = 220;
        int height = 160;
        int left = (this.width - width) / 2;
        int top = (this.height - height) / 2;

        if (mouseClickedInTab(mouseX, mouseY, button, left, top, width, height)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void renderInTab(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, int x, int y, int width, int height) {
        int panelBorder = RPGWaypointsIntegration.getThemeColor("panelBorder", 0xFFDFD0A0); // Gold border
        int slotBg = RPGWaypointsIntegration.getThemeColor("slotBg", 0xFF1C1C1C); // Dark slot background
        int textColor = RPGWaypointsIntegration.getThemeColor("textColor", 0xFFCCCCCC);
        int textActiveColor = RPGWaypointsIntegration.getThemeColor("textActiveColor", 0xFFD4AF37); // Gold text

        // Standalone Header
        if (Minecraft.getInstance().screen == this) {
            graphics.drawString(this.font, this.title, x + 10, y - 14, textActiveColor, false);
        }

        // Draw double borders
        graphics.fill(x, y, x + width, y + height, slotBg);
        UIHelper.drawOutline(graphics, x, y, width, height, panelBorder);
        UIHelper.drawOutline(graphics, x + 2, y + 2, width - 4, height - 4, 0xFF000000);

        int drawX = x + 12;
        int drawY = y + 16;

        // Title text in box
        graphics.drawString(this.font, "§6Enhancer Specifications", drawX, drawY, 0xFFFFFFFF, false);
        drawY += 20;

        // Category Selection Row
        graphics.drawString(this.font, "Category:", drawX, drawY + 2, textColor, false);
        int btnW = 100;
        int btnH = 14;
        int btnX = x + width - btnW - 12;
        graphics.fill(btnX, drawY, btnX + btnW, drawY + btnH, 0xFF2D2D2D);
        UIHelper.drawOutline(graphics, btnX, drawY, btnW, btnH, 0xFF777777);
        String catName = activeCategory.toUpperCase();
        graphics.drawString(this.font, catName, btnX + (btnW - this.font.width(catName)) / 2, drawY + 3, 0xFFFFFFFF, false);
        drawY += 22;

        // Stat Type Row (Interactive Modifies Selection)
        graphics.drawString(this.font, "Modifies:", drawX, drawY + 2, textColor, false);
        graphics.fill(btnX, drawY, btnX + btnW, drawY + btnH, 0xFF2D2D2D);
        UIHelper.drawOutline(graphics, btnX, drawY, btnW, btnH, 0xFF777777);
        String typeName = activeType.toLowerCase();
        graphics.drawString(this.font, typeName, btnX + (btnW - this.font.width(typeName)) / 2, drawY + 3, 0xFFFFFFFF, false);
        drawY += 22;

        // Value Adjuster Row / Ability selection row
        if (activeType.equals("grant_ability")) {
            graphics.drawString(this.font, "Ability:", drawX, drawY + 2, textColor, false);
            // Minus Button
            int valAdjustW = 14;
            int minusX = btnX;
            graphics.fill(minusX, drawY, minusX + valAdjustW, drawY + btnH, 0xFF2D2D2D);
            UIHelper.drawOutline(graphics, minusX, drawY, valAdjustW, btnH, 0xFF777777);
            graphics.drawString(this.font, "<", minusX + 4, drawY + 3, 0xFFFFFFFF, false);

            // Ability display
            java.util.List<String> abNames = getAbilityNames();
            int abilityIdx = (int) Math.min(abNames.size() - 1, Math.max(0, activeValue));
            String abName = abNames.isEmpty() ? "None" : abNames.get(abilityIdx);
            String displayAb = abName.length() > 10 ? abName.substring(0, 8) + ".." : abName;
            int displayW = btnW - 2 * valAdjustW - 4;
            int displayX = minusX + valAdjustW + 2;
            graphics.drawString(this.font, displayAb, displayX + (displayW - this.font.width(displayAb)) / 2, drawY + 3, 0xFFFFFFFF, false);

            // Plus Button
            int plusX = btnX + btnW - valAdjustW;
            graphics.fill(plusX, drawY, plusX + valAdjustW, drawY + btnH, 0xFF2D2D2D);
            UIHelper.drawOutline(graphics, plusX, drawY, valAdjustW, btnH, 0xFF777777);
            graphics.drawString(this.font, ">", plusX + 3, drawY + 3, 0xFFFFFFFF, false);

            // Hover check for ability display
            if (mouseX >= minusX + valAdjustW && mouseX <= plusX && mouseY >= drawY && mouseY <= drawY + btnH) {
                MountData.AbilityData ab = MountRegistry.customAbilities.get(abName);
                if (ab != null) {
                    java.util.List<net.minecraft.network.chat.Component> tooltip = new java.util.ArrayList<>();
                    String baseKey = "ability.rpg_mounts." + ab.name.toLowerCase().replace(" ", "_");
                    Component titleComp = Component.translatable(baseKey);
                    String titleStr = titleComp.getString();
                    if (titleStr.equals(baseKey)) {
                        titleStr = ab.name;
                    }
                    tooltip.add(Component.literal("§6" + titleStr));
                    tooltip.add(Component.literal(ab.isPassive ? "§bPassive Ability" : "§aActive Ability (" + ab.type + ")"));
                    if (!ab.isPassive) {
                        tooltip.add(Component.literal("§eStamina Cost: §f" + ab.staminaCost));
                        tooltip.add(Component.literal("§eCooldown: §f" + ab.cooldownTicks + " ticks"));
                    }
                    if (ab.damage > 0) {
                        tooltip.add(Component.literal("§eDamage: §f" + ab.damage + " (" + ab.damageType + ")"));
                    }
                    
                    Component descComp = Component.translatable(baseKey + ".desc");
                    String descStr = descComp.getString();
                    if (descStr.equals(baseKey + ".desc")) {
                        descStr = ab.description;
                    }
                    if (!descStr.isEmpty()) {
                        tooltip.add(Component.literal("§7" + descStr));
                    }
                    
                    if (ab.allowedCategories != null && !ab.allowedCategories.isEmpty()) {
                        tooltip.add(Component.literal("§8Allowed Categories: " + String.join(", ", ab.allowedCategories)));
                    }
                    if (ab.allowedMountIds != null && !ab.allowedMountIds.isEmpty()) {
                        tooltip.add(Component.literal("§8Allowed Mounts: " + String.join(", ", ab.allowedMountIds)));
                    }
                    this.hoveredTooltip = tooltip;
                }
            }
        } else {
            graphics.drawString(this.font, "Value:", drawX, drawY + 2, textColor, false);
            // Minus Button
            int valAdjustW = 14;
            int minusX = btnX;
            graphics.fill(minusX, drawY, minusX + valAdjustW, drawY + btnH, 0xFF2D2D2D);
            UIHelper.drawOutline(graphics, minusX, drawY, valAdjustW, btnH, 0xFF777777);
            graphics.drawString(this.font, "-", minusX + 4, drawY + 3, 0xFFFFFFFF, false);

            // Value display
            boolean isOneDecimal = activeType.equals("max_health") || activeType.equals("armor") || activeType.equals("flat_damage_reduction") || activeType.equals("damage_boost") || activeType.equals("strength");
            String valStr = String.format(isOneDecimal ? "%.1f" : "%.2f", activeValue);
            int displayW = btnW - 2 * valAdjustW - 4;
            int displayX = minusX + valAdjustW + 2;
            graphics.drawString(this.font, valStr, displayX + (displayW - this.font.width(valStr)) / 2, drawY + 3, 0xFFFFFFFF, false);

            // Plus Button
            int plusX = btnX + btnW - valAdjustW;
            graphics.fill(plusX, drawY, plusX + valAdjustW, drawY + btnH, 0xFF2D2D2D);
            UIHelper.drawOutline(graphics, plusX, drawY, valAdjustW, btnH, 0xFF777777);
            graphics.drawString(this.font, "+", plusX + 3, drawY + 3, 0xFFFFFFFF, false);
        }
        drawY += 26;

        // Action Button: Create
        int actionBtnW = width - 24;
        int actionBtnH = 18;
        int actionBtnX = x + 12;
        graphics.fill(actionBtnX, drawY, actionBtnX + actionBtnW, drawY + actionBtnH, 0xFF005500);
        UIHelper.drawOutline(graphics, actionBtnX, drawY, actionBtnW, actionBtnH, 0xFF888888);
        String actionLabel = "Create Enhancer";
        graphics.drawString(this.font, actionLabel, actionBtnX + (actionBtnW - this.font.width(actionLabel)) / 2, drawY + 5, 0xFFFFFFFF, false);
    }

    public boolean mouseClickedInTab(double mouseX, double mouseY, int button, int x, int y, int width, int height) {
        int drawX = x + 12;
        int drawY = y + 16 + 20;

        int btnW = 100;
        int btnH = 14;
        int btnX = x + width - btnW - 12;

        // Click Category
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= drawY && mouseY <= drawY + btnH) {
            categoryIndex = (categoryIndex + 1) % CATEGORIES.length;
            activeCategory = CATEGORIES[categoryIndex];
            updateDefaultsForCategory();
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        drawY += 22;

        // Click Modifies (Toggles type)
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= drawY && mouseY <= drawY + btnH) {
            String[] stats = STATS_BY_CATEGORY.get(activeCategory);
            if (stats != null && stats.length > 0) {
                int idx = 0;
                for (int i = 0; i < stats.length; i++) {
                    if (stats[i].equalsIgnoreCase(activeType)) {
                        idx = i;
                        break;
                    }
                }
                idx = (idx + 1) % stats.length;
                activeType = stats[idx];
                updateDefaultValueForType();
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        drawY += 22;

        // Click Adjuster Value
        int valAdjustW = 14;
        if (mouseY >= drawY && mouseY <= drawY + btnH) {
            if (activeType.equals("grant_ability")) {
                java.util.List<String> abNames = getAbilityNames();
                if (!abNames.isEmpty()) {
                    int idx = (int) Math.min(abNames.size() - 1, Math.max(0, activeValue));
                    // Minus
                    if (mouseX >= btnX && mouseX <= btnX + valAdjustW) {
                        idx = (idx - 1 + abNames.size()) % abNames.size();
                        activeValue = idx;
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                    // Plus
                    int plusX = btnX + btnW - valAdjustW;
                    if (mouseX >= plusX && mouseX <= plusX + valAdjustW) {
                        idx = (idx + 1) % abNames.size();
                        activeValue = idx;
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                }
            } else {
                double step = getStepValue();
                // Minus
                if (mouseX >= btnX && mouseX <= btnX + valAdjustW) {
                    activeValue = Math.max(step, activeValue - step);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
                // Plus
                int plusX = btnX + btnW - valAdjustW;
                if (mouseX >= plusX && mouseX <= plusX + valAdjustW) {
                    activeValue = activeValue + step;
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }
        drawY += 26;

        // Click Create Button
        int actionBtnW = width - 24;
        int actionBtnH = 18;
        int actionBtnX = x + 12;
        if (mouseX >= actionBtnX && mouseX <= actionBtnX + actionBtnW && mouseY >= drawY && mouseY <= drawY + actionBtnH) {
            // Send creation request to server
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUtf(activeCategory);
            buf.writeUtf(activeType);
            buf.writeDouble(activeValue);
            
            String abName = "";
            if (activeType.equals("grant_ability")) {
                java.util.List<String> abNames = getAbilityNames();
                if (!abNames.isEmpty()) {
                    int abilityIdx = (int) Math.min(abNames.size() - 1, Math.max(0, activeValue));
                    abName = abNames.get(abilityIdx);
                }
            }
            buf.writeUtf(abName);
            
            NetworkManager.sendToServer(ModPackets.C2S_CREATE_ENHANCER, buf);
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));

            if (Minecraft.getInstance().screen == this) {
                this.onClose();
            }
            return true;
        }

        return false;
    }

    private void updateDefaultsForCategory() {
        if (activeCategory.equals("defense")) {
            activeType = "max_health";
            activeValue = 10.0;
        } else if (activeCategory.equals("movement")) {
            activeType = "speed";
            activeValue = 0.10;
        } else if (activeCategory.equals("damage")) {
            activeType = "damage_boost";
            activeValue = 2.0;
        } else if (activeCategory.equals("ability")) {
            activeType = "cooldown_reduction";
            activeValue = 0.10;
        }
    }

    private void updateDefaultValueForType() {
        if (activeType.equals("max_health")) {
            activeValue = 10.0;
        } else if (activeType.equals("armor")) {
            activeValue = 5.0;
        } else if (activeType.equals("flat_damage_reduction")) {
            activeValue = 1.0;
        } else if (activeType.equals("speed")) {
            activeValue = 0.10;
        } else if (activeType.equals("swim_speed")) {
            activeValue = 0.10;
        } else if (activeType.equals("fly_speed")) {
            activeValue = 0.10;
        } else if (activeType.equals("jump_height")) {
            activeValue = 0.10;
        } else if (activeType.equals("jump_strength")) {
            activeValue = 0.10;
        } else if (activeType.equals("damage_boost")) {
            activeValue = 2.0;
        } else if (activeType.equals("strength")) {
            activeValue = 2.0;
        } else if (activeType.equals("attack_speed")) {
            activeValue = 0.10;
        } else if (activeType.equals("cooldown_reduction")) {
            activeValue = 0.10;
        } else if (activeType.equals("stamina_cost_reduction")) {
            activeValue = 0.10;
        } else if (activeType.equals("grant_ability")) {
            activeValue = 0.0;
        }
    }

    private double getStepValue() {
        if (activeType.equals("max_health")) return 5.0;
        if (activeType.equals("armor")) return 1.0;
        if (activeType.equals("flat_damage_reduction")) return 0.5;
        if (activeType.equals("damage_boost") || activeType.equals("strength")) return 0.5;
        return 0.02;
    }

    public boolean isInitialized() {
        return this.minecraft != null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
