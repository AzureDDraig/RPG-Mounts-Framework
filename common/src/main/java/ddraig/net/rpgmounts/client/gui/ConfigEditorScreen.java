package ddraig.net.rpgmounts.client.gui;

import ddraig.net.rpgmounts.client.integration.RPGWaypointsIntegration;
import ddraig.net.rpgmounts.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * RPG Mounts Config Editor Screen
 * Sodium-themed scrollable options list allowing administrators to configure settings.
 * Features toggles and plus/minus adjusters, scroll wheel support, and clickable scrollbar.
 */
public class ConfigEditorScreen extends Screen {

    private static class ConfigOption {
        String name;
        String type; // "boolean", "int", "double", "float", "string"
        java.util.function.Supplier<Object> getter;
        java.util.function.Consumer<Object> setter;
        double min;
        double max;
        double step;
        String[] options; // for string choice
        String tooltipKey;

        ConfigOption(String name, String type, java.util.function.Supplier<Object> getter, java.util.function.Consumer<Object> setter, double min, double max, double step, String tooltipKey) {
            this.name = name;
            this.type = type;
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.step = step;
            this.tooltipKey = tooltipKey;
        }

        ConfigOption(String name, String[] options, java.util.function.Supplier<Object> getter, java.util.function.Consumer<Object> setter, String tooltipKey) {
            this.name = name;
            this.type = "string";
            this.getter = getter;
            this.setter = setter;
            this.options = options;
            this.tooltipKey = tooltipKey;
        }

        ConfigOption(String name) {
            this.name = name;
            this.type = "header";
            this.getter = null;
            this.setter = null;
            this.min = 0;
            this.max = 0;
            this.step = 0;
            this.tooltipKey = "";
        }
    }

    private final List<ConfigOption> optionsList = new ArrayList<>();
    private double scrollAmount = 0.0;
    private String hoveredTooltipKey = null;

    public ConfigEditorScreen() {
        super(Component.literal("RPG Mounts Server Config"));
        initOptions();
    }

    private void initOptions() {
        optionsList.clear();

        // GENERAL SECTION
        optionsList.add(new ConfigOption("=== General Settings ==="));
        optionsList.add(new ConfigOption("Use Dimension Whitelist", "boolean",
            () -> ModConfig.get().general.useWhitelist,
            val -> ModConfig.get().general.useWhitelist = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.use_whitelist"));
        optionsList.add(new ConfigOption("Summon In Water", "boolean",
            () -> ModConfig.get().general.allowSummoningInWater,
            val -> ModConfig.get().general.allowSummoningInWater = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.summon_in_water"));
        optionsList.add(new ConfigOption("Summon In Lava", "boolean",
            () -> ModConfig.get().general.allowSummoningInLava,
            val -> ModConfig.get().general.allowSummoningInLava = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.summon_in_lava"));
        optionsList.add(new ConfigOption("Summon Submerged", "boolean",
            () -> ModConfig.get().general.allowSummoningSubmerged,
            val -> ModConfig.get().general.allowSummoningSubmerged = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.summon_submerged"));
        optionsList.add(new ConfigOption("Manual Dismissal", "boolean",
            () -> ModConfig.get().general.require_manual_dismissal,
            val -> ModConfig.get().general.require_manual_dismissal = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.manual_dismissal"));
        optionsList.add(new ConfigOption("Dismiss On Dismount", "boolean",
            () -> ModConfig.get().general.auto_dismiss_on_dismount,
            val -> ModConfig.get().general.auto_dismiss_on_dismount = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.dismiss_on_dismount"));
        optionsList.add(new ConfigOption("Idle Despawn (secs)", "int", 
            () -> ModConfig.get().general.autoDespawnIdleSeconds, 
            val -> ModConfig.get().general.autoDespawnIdleSeconds = ((Number) val).intValue(), 0, 3600, 60,
            "config.rpgmounts.tooltip.idle_despawn_secs"));
        optionsList.add(new ConfigOption("Multi Passenger", "boolean",
            () -> ModConfig.get().general.enableMultiPassenger,
            val -> ModConfig.get().general.enableMultiPassenger = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.multi_passenger"));
        optionsList.add(new ConfigOption("Culling Range (blocks)", "int", 
            () -> ModConfig.get().general.culling_distance_blocks, 
            val -> ModConfig.get().general.culling_distance_blocks = ((Number) val).intValue(), 16, 256, 8,
            "config.rpgmounts.tooltip.culling_range_blocks"));
        optionsList.add(new ConfigOption("Prevent Duplicate Mounts", "boolean",
            () -> ModConfig.get().general.prevent_duplicate_mounts,
            val -> ModConfig.get().general.prevent_duplicate_mounts = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.prevent_duplicate_mounts"));

        // STATS LIMITS SECTION
        optionsList.add(new ConfigOption("=== Health & Speed Bounds ==="));
        optionsList.add(new ConfigOption("Min Allowed Health", "double",
            () -> ModConfig.get().stats.min_health_allowed,
            val -> ModConfig.get().stats.min_health_allowed = ((Number) val).doubleValue(), 1.0, 100.0, 1.0,
            "config.rpgmounts.tooltip.min_allowed_health"));
        optionsList.add(new ConfigOption("Max Allowed Health", "double",
            () -> ModConfig.get().stats.max_health_allowed,
            val -> ModConfig.get().stats.max_health_allowed = ((Number) val).doubleValue(), 10.0, 100000.0, 50.0,
            "config.rpgmounts.tooltip.max_allowed_health"));
        optionsList.add(new ConfigOption("Min Allowed Speed", "double",
            () -> ModConfig.get().stats.min_speed_allowed,
            val -> ModConfig.get().stats.min_speed_allowed = ((Number) val).doubleValue(), 0.01, 1.0, 0.01,
            "config.rpgmounts.tooltip.min_allowed_speed"));
        optionsList.add(new ConfigOption("Max Allowed Speed", "double",
            () -> ModConfig.get().stats.max_speed_allowed,
            val -> ModConfig.get().stats.max_speed_allowed = ((Number) val).doubleValue(), 0.1, 10.0, 0.1,
            "config.rpgmounts.tooltip.max_allowed_speed"));

        // STAMINA & MOVEMENT SECTION
        optionsList.add(new ConfigOption("=== Stamina & Controls ==="));
        optionsList.add(new ConfigOption("Stamina System", "boolean", 
            () -> ModConfig.get().stamina.enable_stamina_system, 
            val -> ModConfig.get().stamina.enable_stamina_system = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.stamina_system"));
        optionsList.add(new ConfigOption("Sprint Stamina Cost", "double",
            () -> ModConfig.get().stamina.sprint_stamina_cost_per_second,
            val -> ModConfig.get().stamina.sprint_stamina_cost_per_second = ((Number) val).doubleValue(), 0.0, 100.0, 1.0,
            "config.rpgmounts.tooltip.sprint_stamina_cost"));
        optionsList.add(new ConfigOption("Flight Stamina Cost", "double",
            () -> ModConfig.get().stamina.flight_stamina_cost_per_second,
            val -> ModConfig.get().stamina.flight_stamina_cost_per_second = ((Number) val).doubleValue(), 0.0, 100.0, 1.0,
            "config.rpgmounts.tooltip.flight_stamina_cost"));
        optionsList.add(new ConfigOption("Flight Descent Regen", "double",
            () -> ModConfig.get().stamina.flight_descent_stamina_regenerate_ratio,
            val -> ModConfig.get().stamina.flight_descent_stamina_regenerate_ratio = ((Number) val).doubleValue(), 0.0, 2.0, 0.05,
            "config.rpgmounts.tooltip.flight_descent_regen"));
        optionsList.add(new ConfigOption("Saddleless Jumping", "boolean", 
            () -> ModConfig.get().stamina.allow_saddleless_jumping, 
            val -> ModConfig.get().stamina.allow_saddleless_jumping = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.saddleless_jumping"));
        optionsList.add(new ConfigOption("Saddleless Sprinting", "boolean", 
            () -> ModConfig.get().stamina.allow_saddleless_sprinting, 
            val -> ModConfig.get().stamina.allow_saddleless_sprinting = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.saddleless_sprinting"));
        optionsList.add(new ConfigOption("Saddle Speed Boost", "double", 
            () -> ModConfig.get().stamina.saddle_speed_boost_multiplier, 
            val -> ModConfig.get().stamina.saddle_speed_boost_multiplier = ((Number) val).doubleValue(), 0.0, 1.0, 0.05,
            "config.rpgmounts.tooltip.saddle_speed_boost"));

        // BONDING & LEVELING SECTION
        optionsList.add(new ConfigOption("=== Bonding & Leveling ==="));
        optionsList.add(new ConfigOption("Bonding Buffs", "boolean",
            () -> ModConfig.get().bondingAndLeveling.enable_bonding_buffs,
            val -> ModConfig.get().bondingAndLeveling.enable_bonding_buffs = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.bonding_buffs"));
        optionsList.add(new ConfigOption("Bonding Speed Boost", "float",
            () -> ModConfig.get().bondingAndLeveling.bonding_speed_multiplier,
            val -> ModConfig.get().bondingAndLeveling.bonding_speed_multiplier = ((Number) val).floatValue(), 0.0f, 1.0f, 0.01f,
            "config.rpgmounts.tooltip.bonding_speed_boost"));
        optionsList.add(new ConfigOption("Bonding Health Boost", "float",
            () -> ModConfig.get().bondingAndLeveling.bonding_health_multiplier,
            val -> ModConfig.get().bondingAndLeveling.bonding_health_multiplier = ((Number) val).floatValue(), 0.0f, 1.0f, 0.01f,
            "config.rpgmounts.tooltip.bonding_health_boost"));
        optionsList.add(new ConfigOption("Mount Levelling", "boolean", 
            () -> ModConfig.get().bondingAndLeveling.enableMountLevelling, 
            val -> ModConfig.get().bondingAndLeveling.enableMountLevelling = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.mount_levelling"));
        optionsList.add(new ConfigOption("Base XP Requirement", "double", 
            () -> ModConfig.get().bondingAndLeveling.baseXpRequirement, 
            val -> ModConfig.get().bondingAndLeveling.baseXpRequirement = ((Number) val).doubleValue(), 10.0, 10000.0, 50.0,
            "config.rpgmounts.tooltip.base_xp_requirement"));
        optionsList.add(new ConfigOption("XP Exponent", "double", 
            () -> ModConfig.get().bondingAndLeveling.xpExponent, 
            val -> ModConfig.get().bondingAndLeveling.xpExponent = ((Number) val).doubleValue(), 1.0, 5.0, 0.1,
            "config.rpgmounts.tooltip.xp_exponent"));
        optionsList.add(new ConfigOption("Combat XP Dealt Ratio", "double", 
            () -> ModConfig.get().bondingAndLeveling.combatXpDealtRatio, 
            val -> ModConfig.get().bondingAndLeveling.combatXpDealtRatio = ((Number) val).doubleValue(), 0.0, 10.0, 0.1,
            "config.rpgmounts.tooltip.combat_xp_dealt_ratio"));
        optionsList.add(new ConfigOption("Combat XP Taken Ratio", "double", 
            () -> ModConfig.get().bondingAndLeveling.combatXpTakenRatio, 
            val -> ModConfig.get().bondingAndLeveling.combatXpTakenRatio = ((Number) val).doubleValue(), 0.0, 10.0, 0.1,
            "config.rpgmounts.tooltip.combat_xp_taken_ratio"));
        optionsList.add(new ConfigOption("Riding XP Per Second", "double", 
            () -> ModConfig.get().bondingAndLeveling.ridingXpPerSecond, 
            val -> ModConfig.get().bondingAndLeveling.ridingXpPerSecond = ((Number) val).doubleValue(), 0.0, 10.0, 0.1,
            "config.rpgmounts.tooltip.riding_xp_per_second"));

        // COMBAT & ENHANCERS SECTION
        optionsList.add(new ConfigOption("=== Combat & Enhancers ==="));
        optionsList.add(new ConfigOption("Combat Abilities", "boolean", 
            () -> ModConfig.get().combatAndEnhancers.enable_combat_abilities, 
            val -> ModConfig.get().combatAndEnhancers.enable_combat_abilities = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.combat_abilities"));
        optionsList.add(new ConfigOption("Rider Reach Mixin", "boolean", 
            () -> ModConfig.get().combatAndEnhancers.enable_rider_reach_mixin, 
            val -> ModConfig.get().combatAndEnhancers.enable_rider_reach_mixin = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.rider_reach_mixin"));
        optionsList.add(new ConfigOption("Reach Offset (blocks)", "float", 
            () -> ModConfig.get().combatAndEnhancers.rider_reach_offset, 
            val -> ModConfig.get().combatAndEnhancers.rider_reach_offset = ((Number) val).floatValue(), 0.0f, 5.0f, 0.2f,
            "config.rpgmounts.tooltip.reach_offset_blocks"));
        optionsList.add(new ConfigOption("Enable Enhancers", "boolean", 
            () -> ModConfig.get().combatAndEnhancers.enable_enhancers, 
            val -> ModConfig.get().combatAndEnhancers.enable_enhancers = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.enable_enhancers"));
        optionsList.add(new ConfigOption("Max Enhancers (Defense)", "int", 
            () -> ModConfig.get().combatAndEnhancers.max_enhancers_defense, 
            val -> ModConfig.get().combatAndEnhancers.max_enhancers_defense = ((Number) val).intValue(), 1, 10, 1,
            "config.rpgmounts.tooltip.max_enhancers_defense"));
        optionsList.add(new ConfigOption("Max Enhancers (Movement)", "int", 
            () -> ModConfig.get().combatAndEnhancers.max_enhancers_movement, 
            val -> ModConfig.get().combatAndEnhancers.max_enhancers_movement = ((Number) val).intValue(), 1, 10, 1,
            "config.rpgmounts.tooltip.max_enhancers_movement"));
        optionsList.add(new ConfigOption("Max Enhancers (Damage)", "int", 
            () -> ModConfig.get().combatAndEnhancers.max_enhancers_damage, 
            val -> ModConfig.get().combatAndEnhancers.max_enhancers_damage = ((Number) val).intValue(), 1, 10, 1,
            "config.rpgmounts.tooltip.max_enhancers_damage"));
        optionsList.add(new ConfigOption("Max Enhancers (Ability)", "int", 
            () -> ModConfig.get().combatAndEnhancers.max_enhancers_ability, 
            val -> ModConfig.get().combatAndEnhancers.max_enhancers_ability = ((Number) val).intValue(), 1, 10, 1,
            "config.rpgmounts.tooltip.max_enhancers_ability"));

        // SAFETY & MORTALITY SECTION
        optionsList.add(new ConfigOption("=== Mortality & Safety ==="));
        optionsList.add(new ConfigOption("Fall Protection", "boolean", 
            () -> ModConfig.get().mortalityAndSafety.enable_fall_protection, 
            val -> ModConfig.get().mortalityAndSafety.enable_fall_protection = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.fall_protection"));
        optionsList.add(new ConfigOption("Fall Protect Time", "int",
            () -> ModConfig.get().mortalityAndSafety.fall_protection_seconds,
            val -> ModConfig.get().mortalityAndSafety.fall_protection_seconds = ((Number) val).intValue(), 1, 30, 1,
            "config.rpgmounts.tooltip.fall_protect_time"));
        optionsList.add(new ConfigOption("Mortality Mode", new String[]{"Timer", "Permadeath", "Item"}, 
            () -> ModConfig.get().mortalityAndSafety.mounts_mortality, 
            val -> ModConfig.get().mortalityAndSafety.mounts_mortality = (String) val,
            "config.rpgmounts.tooltip.mortality_mode"));
        optionsList.add(new ConfigOption("Mortality Cooldown", "int",
            () -> ModConfig.get().mortalityAndSafety.mounts_mortality_cooldown_ticks,
            val -> ModConfig.get().mortalityAndSafety.mounts_mortality_cooldown_ticks = ((Number) val).intValue(), 20, 72000, 100,
            "config.rpgmounts.tooltip.mortality_cooldown"));

        // EVOLUTION SECTION
        optionsList.add(new ConfigOption("=== Evolution Settings ==="));
        optionsList.add(new ConfigOption("Evolution Heritage", "boolean", 
            () -> ModConfig.get().evolution.enable_evolution_heritage, 
            val -> ModConfig.get().evolution.enable_evolution_heritage = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.evolution_heritage"));
        optionsList.add(new ConfigOption("Evolution Policy", new String[]{"RETAIN", "DEGRADE", "RESET"}, 
            () -> ModConfig.get().evolution.evolution_level_policy, 
            val -> ModConfig.get().evolution.evolution_level_policy = (String) val,
            "config.rpgmounts.tooltip.evolution_policy"));
        optionsList.add(new ConfigOption("Evolution Degrade %", "double", 
            () -> ModConfig.get().evolution.evolution_degrade_percentage, 
            val -> ModConfig.get().evolution.evolution_degrade_percentage = ((Number) val).doubleValue(), 0.0, 1.0, 0.05,
            "config.rpgmounts.tooltip.evolution_degrade_pct"));
        optionsList.add(new ConfigOption("Chroma Mutations", "boolean", 
            () -> ModConfig.get().evolution.enable_chroma_mutations, 
            val -> ModConfig.get().evolution.enable_chroma_mutations = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.chroma_mutations"));
        optionsList.add(new ConfigOption("Chroma Mutation Chance", "double", 
            () -> ModConfig.get().evolution.chroma_mutation_chance, 
            val -> ModConfig.get().evolution.chroma_mutation_chance = ((Number) val).doubleValue(), 0.0, 1.0, 0.01,
            "config.rpgmounts.tooltip.chroma_mutation_chance"));
        optionsList.add(new ConfigOption("Environmental Triggers", "boolean", 
            () -> ModConfig.get().evolution.enable_environmental_triggers, 
            val -> ModConfig.get().evolution.enable_environmental_triggers = (boolean) val, 0, 0, 0,
            "config.rpgmounts.tooltip.environmental_triggers"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (Minecraft.getInstance().screen == this) {
            this.renderBackground(graphics);
        }

        int width = 300;
        int height = 190;
        int left = (this.width - width) / 2;
        int top = (this.height - height) / 2;

        renderInTab(graphics, mouseX, mouseY, partialTicks, left, top, width, height);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int visibleH = this.height - 34; // approximate height for standalone screen
        int totalH = optionsList.size() * 18;
        double maxScroll = Math.max(0, totalH - visibleH);
        this.scrollAmount = Mth.clamp(this.scrollAmount - amount * 12, 0, maxScroll);
        return true;
    }

    public boolean mouseScrolledInTab(double mouseX, double mouseY, double amount, int x, int y, int width, int height) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            int visibleH = height - 16;
            int totalH = optionsList.size() * 18;
            double maxScroll = Math.max(0, totalH - visibleH);
            this.scrollAmount = Mth.clamp(this.scrollAmount - amount * 12, 0, maxScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int width = 300;
        int height = 190;
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
        int textActiveColor = RPGWaypointsIntegration.getThemeColor("textActiveColor", 0xFFD4AF37); // Gold text

        // Header Title (if standalone)
        if (Minecraft.getInstance().screen == this) {
            graphics.drawString(this.font, this.title, x + 10, y - 14, textActiveColor, false);
        }

        // Draw double borders
        graphics.fill(x, y, x + width, y + height, slotBg);
        UIHelper.drawOutline(graphics, x, y, width, height, panelBorder);
        UIHelper.drawOutline(graphics, x + 2, y + 2, width - 4, height - 4, 0xFF000000);

        int visibleH = height - 16;
        int clipY1 = y + 4;
        int clipY2 = y + height - 4;
        
        if (Minecraft.getInstance().screen == this) {
            visibleH = height - 34; // leave space for Save/Cancel buttons
            clipY2 = y + height - 26;
        }

        int totalH = optionsList.size() * 18;
        double maxScroll = Math.max(0, totalH - visibleH);
        int barH = Math.max(10, (visibleH * visibleH) / totalH);

        // Reset hoveredTooltipKey each frame
        this.hoveredTooltipKey = null;

        // Draw Options list with scissor clipping
        graphics.enableScissor(x + 4, clipY1, x + width - 12, clipY2);

        int startY = y + 8 - (int) scrollAmount;
        int rowH = 18;
        int usableW = width - 18; // Leave space for scrollbar track
        int startX = x + 6;

        for (int i = 0; i < optionsList.size(); i++) {
            ConfigOption opt = optionsList.get(i);
            int rowY = startY + i * rowH;
            
            if (rowY + 16 >= clipY1 && rowY <= clipY2) {
                // Check if mouse is hovering over this row (excluding scrollbar track)
                if (mouseX >= startX && mouseX <= startX + usableW && mouseY >= rowY && mouseY <= rowY + 16 && mouseY >= clipY1 && mouseY <= clipY2) {
                    this.hoveredTooltipKey = opt.tooltipKey;
                }

                if (opt.type.equals("header")) {
                    graphics.drawString(this.font, opt.name, startX + 4, rowY + 4, 0xFFDFD0A0, false);
                    graphics.fill(startX + 4, rowY + 14, startX + usableW - 4, rowY + 15, 0x30FFFFFF); // Underline divider
                } else if (opt.type.equals("boolean")) {
                    boolean val = (boolean) opt.getter.get();
                    drawSodiumRow(graphics, opt.name, val ? "ENABLED" : "DISABLED", val, startX, rowY, usableW, mouseX, mouseY);
                } else if (opt.type.equals("string")) {
                    String val = (String) opt.getter.get();
                    drawSodiumRow(graphics, opt.name, val.toUpperCase(), true, startX, rowY, usableW, mouseX, mouseY);
                } else {
                    String displayVal;
                    if (opt.type.equals("int")) {
                        displayVal = String.valueOf(opt.getter.get());
                    } else {
                        displayVal = String.format("%.2f", ((Number) opt.getter.get()).doubleValue());
                    }
                    drawSodiumAdjustableRow(graphics, opt.name, displayVal, startX, rowY, usableW, mouseX, mouseY);
                }
            }
        }
        graphics.disableScissor();

        // Draw Scrollbar Track and Handle
        if (maxScroll > 0) {
            int barX = x + width - 10;
            int barY = y + 8 + (int) ((scrollAmount / maxScroll) * (visibleH - barH));
            // Draw background track
            graphics.fill(barX, y + 8, barX + 6, y + 8 + visibleH, 0x1AFFFFFF);
            // Draw handle
            graphics.fill(barX, barY, barX + 6, barY + barH, 0x50FFFFFF);
        }

        // Save & Cancel buttons if standalone
        if (Minecraft.getInstance().screen == this) {
            int btnW = 80;
            int btnH = 16;
            int btnY = y + height - 22;
            
            // Save
            int saveX = x + 20;
            boolean hoverSave = mouseX >= saveX && mouseX <= saveX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
            UIHelper.drawShadedButton(graphics, saveX, btnY, btnW, btnH, hoverSave, 0xFF005500);
            graphics.drawString(this.font, "Save", saveX + 28, btnY + 4, 0xFFFFFFFF, false);
            
            // Cancel
            int cancelX = x + width - btnW - 20;
            boolean hoverCancel = mouseX >= cancelX && mouseX <= cancelX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
            UIHelper.drawShadedButton(graphics, cancelX, btnY, btnW, btnH, hoverCancel, 0xFF550000);
            graphics.drawString(this.font, "Cancel", cancelX + 22, btnY + 4, 0xFFFFFFFF, false);
        }

        // Render hovered tooltip at the very end so it draws above everything else!
        if (this.hoveredTooltipKey != null && !this.hoveredTooltipKey.isEmpty()) {
            graphics.renderComponentTooltip(this.font, List.of(Component.translatable(hoveredTooltipKey)), mouseX, mouseY);
        }
    }

    private void drawSodiumRow(GuiGraphics graphics, String label, String value, boolean active, int x, int y, int width, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16;
        graphics.fill(x, y, x + width, y + 16, hover ? 0x20FFFFFF : 0x05FFFFFF);
        graphics.drawString(this.font, label, x + 6, y + 4, 0xFFE0E0E0, false);
        
        int btnW = 55;
        int btnH = 12;
        int btnX = x + width - btnW - 4;
        int btnY = y + 2;
        int btnBg = active ? 0xFF006600 : 0xFF3D3D3D;
        boolean hoverBtn = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        UIHelper.drawShadedButton(graphics, btnX, btnY, btnW, btnH, hoverBtn, btnBg);
        graphics.drawString(this.font, value, btnX + (btnW - this.font.width(value)) / 2, btnY + 2, 0xFFFFFFFF, false);
    }

    private void drawSodiumAdjustableRow(GuiGraphics graphics, String label, String value, int x, int y, int width, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16;
        graphics.fill(x, y, x + width, y + 16, hover ? 0x20FFFFFF : 0x05FFFFFF);
        graphics.drawString(this.font, label, x + 6, y + 4, 0xFFE0E0E0, false);
        
        int btnW = 12;
        int btnH = 12;
        int btnY = y + 2;
        
        // Value label
        int valW = 60;
        int valX = x + width - valW - btnW - 4;
        graphics.drawString(this.font, value, valX + (valW - this.font.width(value)) / 2, y + 4, 0xFFE0E0E0, false);
        
        // Minus Button
        int minusX = valX - btnW - 2;
        boolean hoverMinus = mouseX >= minusX && mouseX <= minusX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        UIHelper.drawShadedButton(graphics, minusX, btnY, btnW, btnH, hoverMinus, 0xFF2D2D2D);
        graphics.drawString(this.font, "-", minusX + 4, btnY + 2, 0xFFFFFFFF, false);
        
        // Plus Button
        int plusX = x + width - btnW - 4;
        boolean hoverPlus = mouseX >= plusX && mouseX <= plusX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        UIHelper.drawShadedButton(graphics, plusX, btnY, btnW, btnH, hoverPlus, 0xFF2D2D2D);
        graphics.drawString(this.font, "+", plusX + 3, btnY + 2, 0xFFFFFFFF, false);
    }

    public boolean mouseClickedInTab(double mouseX, double mouseY, int button, int x, int y, int width, int height) {
        int visibleH = height - 16;
        if (Minecraft.getInstance().screen == this) {
            visibleH = height - 34;
        }
        int totalH = optionsList.size() * 18;
        double maxScroll = Math.max(0, totalH - visibleH);

        // Click on Scrollbar Track
        int barX = x + width - 10;
        if (maxScroll > 0 && mouseX >= barX - 2 && mouseX <= x + width - 2 && mouseY >= y + 8 && mouseY <= y + 8 + visibleH) {
            double relativeY = mouseY - (y + 8);
            double scrollPercent = relativeY / (double) visibleH;
            this.scrollAmount = Mth.clamp(scrollPercent * totalH - (visibleH / 2.0), 0.0, maxScroll);
            return true;
        }

        int startY = y + 8 - (int) scrollAmount;
        int rowH = 18;
        int usableW = width - 18;
        int startX = x + 6;

        for (int i = 0; i < optionsList.size(); i++) {
            ConfigOption opt = optionsList.get(i);
            int rowY = startY + i * rowH;

            // Only register clicks on visible rows
            int clipY1 = y + 4;
            int clipY2 = y + height - 4;
            if (Minecraft.getInstance().screen == this) {
                clipY2 = y + height - 26;
            }

            if (rowY >= clipY1 - 8 && rowY + 16 <= clipY2 + 8) {
                if (opt.type.equals("header")) {
                    continue;
                }
                if (opt.type.equals("boolean")) {
                    if (mouseX >= startX && mouseX <= startX + usableW && mouseY >= rowY && mouseY <= rowY + 16) {
                        boolean val = (boolean) opt.getter.get();
                        opt.setter.accept(!val);
                        saveAndSync();
                        return true;
                    }
                } else if (opt.type.equals("string")) {
                    if (mouseX >= startX && mouseX <= startX + usableW && mouseY >= rowY && mouseY <= rowY + 16) {
                        String val = (String) opt.getter.get();
                        int currentIdx = 0;
                        for (int j = 0; j < opt.options.length; j++) {
                            if (opt.options[j].equalsIgnoreCase(val)) {
                                currentIdx = j;
                                break;
                            }
                        }
                        int nextIdx = (currentIdx + 1) % opt.options.length;
                        opt.setter.accept(opt.options[nextIdx]);
                        saveAndSync();
                        return true;
                    }
                } else {
                    int valW = 60;
                    int valX = startX + usableW - valW - 12 - 4;
                    int minusX = valX - 12 - 2;
                    int plusX = startX + usableW - 12 - 4;

                    if (mouseY >= rowY && mouseY <= rowY + 16) {
                        if (mouseX >= minusX && mouseX <= minusX + 12) {
                            double val = ((Number) opt.getter.get()).doubleValue();
                            double newVal = Math.max(opt.min, val - opt.step);
                            if (opt.type.equals("int")) {
                                opt.setter.accept((int) newVal);
                            } else if (opt.type.equals("float")) {
                                opt.setter.accept((float) newVal);
                            } else {
                                opt.setter.accept(newVal);
                            }
                            saveAndSync();
                            return true;
                        } else if (mouseX >= plusX && mouseX <= plusX + 12) {
                            double val = ((Number) opt.getter.get()).doubleValue();
                            double newVal = Math.min(opt.max, val + opt.step);
                            if (opt.type.equals("int")) {
                                opt.setter.accept((int) newVal);
                            } else if (opt.type.equals("float")) {
                                opt.setter.accept((float) newVal);
                            } else {
                                opt.setter.accept(newVal);
                            }
                            saveAndSync();
                            return true;
                        }
                    }
                }
            }
        }

        // Standalone Buttons (Save & Cancel)
        if (Minecraft.getInstance().screen == this) {
            int btnY = y + height - 22;
            // Save
            if (mouseX >= x + 20 && mouseX <= x + 100 && mouseY >= btnY && mouseY <= btnY + 16) {
                saveAndSync();
                this.onClose();
                return true;
            }
            // Cancel
            if (mouseX >= x + width - 100 && mouseX <= x + width - 20 && mouseY >= btnY && mouseY <= btnY + 16) {
                this.onClose();
                return true;
            }
        }

        return false;
    }

    private void saveAndSync() {
        ddraig.net.rpgmounts.config.ModConfig.get().save();
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(new com.google.gson.Gson().toJson(ddraig.net.rpgmounts.config.ModConfig.get()));
        dev.architectury.networking.NetworkManager.sendToServer(ddraig.net.rpgmounts.network.ModPackets.C2S_SAVE_CONFIG, buf);
    }

    public boolean isInitialized() {
        return this.minecraft != null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
