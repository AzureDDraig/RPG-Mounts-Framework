package ddraig.net.rpgmounts.client.gui;

import ddraig.net.rpgmounts.client.integration.RPGWaypointsIntegration;
import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import dev.architectury.networking.NetworkManager;
import ddraig.net.rpgmounts.network.ModPackets;
import ddraig.net.rpgmounts.client.RPGMountsClient;
import ddraig.net.rpgmounts.config.ModConfig;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * RPG Mounts Management HUD Screen
 * Renders the categorized list, 3D viewport, stats progress bars, and summoning trigger.
 * Supports rendering as a sub-panel tab inside the Waypoints compass UI.
 */
public class MountHUDScreen extends Screen {
    private final List<RPGMountsClient.UnlockedMountInfo> groundMounts = new ArrayList<>();
    private final List<RPGMountsClient.UnlockedMountInfo> aquaticMounts = new ArrayList<>();
    private final List<RPGMountsClient.UnlockedMountInfo> flyingMounts = new ArrayList<>();
    private final List<SidebarInstanceItem> sidebarItems = new ArrayList<>();

    private RPGMountsClient.UnlockedMountInfo selectedInstance;
    private MountData selectedMount;
    private float previewRotation = 0.0f;
    private RPGMountEntity previewEntity;
    
    private boolean isDragging = false;
    private double lastMouseX = 0.0;
    private float previewZoom = 1.0f;
    private int listScrollOffset = 0;

    private List<Component> hoveredTooltip = null;

    private String activeTab = "STATS";
    private double statsScrollAmount = 0.0;
    private double ancestryScrollAmount = 0.0;

    private static class SidebarInstanceItem {
        final boolean isHeader;
        final String category;
        final RPGMountsClient.UnlockedMountInfo instance;

        SidebarInstanceItem(String category) {
            this.isHeader = true;
            this.category = category;
            this.instance = null;
        }

        SidebarInstanceItem(RPGMountsClient.UnlockedMountInfo instance) {
            this.isHeader = false;
            this.category = null;
            this.instance = instance;
        }
    }

    private int getStatsTotalHeight() {
        return 190;
    }

    private List<String> getAncestryLines() {
        List<String> lines = new ArrayList<>();
        if (selectedInstance != null) {
            RPGMountsClient.UnlockedMountInfo info = selectedInstance;
            if (info != null && info.ancestryLog != null) {
                try {
                    com.google.gson.JsonArray arr = new com.google.gson.JsonParser().parse(info.ancestryLog).getAsJsonArray();
                    for (int i = 0; i < arr.size(); i++) {
                        com.google.gson.JsonObject obj = arr.get(i).getAsJsonObject();
                        String action = obj.has("action") ? obj.get("action").getAsString() : "";
                        if (action.equalsIgnoreCase("EVOLVED")) {
                            String fromId = obj.has("fromId") ? obj.get("fromId").getAsString() : "";
                            String toId = obj.has("toId") ? obj.get("toId").getAsString() : "";
                            int lvl = obj.has("level") ? obj.get("level").getAsInt() : 1;
                            
                            MountData from = MountRegistry.getTemplate(fromId);
                            MountData to = MountRegistry.getTemplate(toId);
                            String fromName = (from != null) ? from.name : fromId;
                            String toName = (to != null) ? to.name : toId;
                            
                            lines.add(net.minecraft.client.resources.language.I18n.get("gui.rpg_mounts.ancestry.evolved_from", fromName));
                            lines.add(net.minecraft.client.resources.language.I18n.get("gui.rpg_mounts.ancestry.evolved_to", toName, lvl));
                        } else {
                            lines.add(net.minecraft.client.resources.language.I18n.get("gui.rpg_mounts.ancestry.action", action));
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        if (lines.isEmpty()) {
            lines.add(net.minecraft.client.resources.language.I18n.get("gui.rpg_mounts.ancestry.none"));
        }
        return lines;
    }

    private int getAncestryTotalHeight() {
        return getAncestryLines().size() * 11;
    }

    private void drawStatRow(GuiGraphics graphics, String label, String value, int x, int y, int contentW, int contentY, int contentH, int mouseX, int mouseY, String tooltipKey) {
        int textColor = RPGWaypointsIntegration.getThemeColor("textColor", 0xFFCCCCCC);
        int textActiveColor = RPGWaypointsIntegration.getThemeColor("textActiveColor", 0xFFD4AF37);
        
        if (y + 10 >= contentY && y <= contentY + contentH) {
            String displayLabel = label + ":";
            int maxLabelWidth = contentW - this.font.width(value) - 8;
            if (this.font.width(displayLabel) > maxLabelWidth) {
                displayLabel = this.font.plainSubstrByWidth(displayLabel, maxLabelWidth - this.font.width("...")) + "...";
            }
            graphics.drawString(this.font, displayLabel, x, y, textColor, false);
            graphics.drawString(this.font, value, x + contentW - this.font.width(value) - 4, y, textActiveColor, false);
        }
        
        if (mouseX >= x && mouseX <= x + contentW - 12 && mouseY >= y && mouseY <= y + 10) {
            if (mouseY >= contentY && mouseY <= contentY + contentH) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable(tooltipKey));
                hoveredTooltip = tooltip;
            }
        }
    }

    public MountHUDScreen() {
        super(Component.translatable("gui.rpg_mounts.hud.title"));
    }

    @Override
    protected void init() {
        groundMounts.clear();
        aquaticMounts.clear();
        flyingMounts.clear();

        // Populate lists from unlocked tames
        for (RPGMountsClient.UnlockedMountInfo info : RPGMountsClient.unlockedMounts.values()) {
            MountData m = MountRegistry.getTemplate(info.mountId);
            if (m != null) {
                if (m.category.equalsIgnoreCase("GROUND")) {
                    groundMounts.add(info);
                } else if (m.category.equalsIgnoreCase("AQUATIC") || m.category.equalsIgnoreCase("SURFACE_WATER")) {
                    aquaticMounts.add(info);
                } else if (m.category.equalsIgnoreCase("FLYING")) {
                    flyingMounts.add(info);
                }
            }
        }

        java.util.Comparator<RPGMountsClient.UnlockedMountInfo> comp = (a, b) -> {
            MountData mA = MountRegistry.getTemplate(a.mountId);
            MountData mB = MountRegistry.getTemplate(b.mountId);
            String nameA = (a.customName != null && !a.customName.isEmpty()) ? a.customName : (mA != null && mA.name != null && !mA.name.isEmpty() ? mA.name : a.mountId);
            String nameB = (b.customName != null && !b.customName.isEmpty()) ? b.customName : (mB != null && mB.name != null && !mB.name.isEmpty() ? mB.name : b.mountId);
            return nameA.compareToIgnoreCase(nameB);
        };
        groundMounts.sort(comp);
        aquaticMounts.sort(comp);
        flyingMounts.sort(comp);

        if (selectedInstance == null) {
            if (!groundMounts.isEmpty()) selectedInstance = groundMounts.get(0);
            else if (!aquaticMounts.isEmpty()) selectedInstance = aquaticMounts.get(0);
            else if (!flyingMounts.isEmpty()) selectedInstance = flyingMounts.get(0);
        } else {
            if (!RPGMountsClient.unlockedMounts.containsKey(selectedInstance.instanceId)) {
                selectedInstance = null;
                if (!groundMounts.isEmpty()) selectedInstance = groundMounts.get(0);
                else if (!aquaticMounts.isEmpty()) selectedInstance = aquaticMounts.get(0);
                else if (!flyingMounts.isEmpty()) selectedInstance = flyingMounts.get(0);
            } else {
                selectedInstance = RPGMountsClient.unlockedMounts.get(selectedInstance.instanceId);
            }
        }
        selectedMount = selectedInstance != null ? MountRegistry.getTemplate(selectedInstance.mountId) : null;

        // Build sidebar instance items list
        sidebarItems.clear();
        boolean firstGround = true;
        for (RPGMountsClient.UnlockedMountInfo info : groundMounts) {
            if (firstGround) {
                sidebarItems.add(new SidebarInstanceItem("GROUND"));
                firstGround = false;
            }
            sidebarItems.add(new SidebarInstanceItem(info));
        }
        boolean firstAquatic = true;
        for (RPGMountsClient.UnlockedMountInfo info : aquaticMounts) {
            if (firstAquatic) {
                sidebarItems.add(new SidebarInstanceItem("AQUATIC"));
                firstAquatic = false;
            }
            sidebarItems.add(new SidebarInstanceItem(info));
        }
        boolean firstFlying = true;
        for (RPGMountsClient.UnlockedMountInfo info : flyingMounts) {
            if (firstFlying) {
                sidebarItems.add(new SidebarInstanceItem("FLYING"));
                firstFlying = false;
            }
            sidebarItems.add(new SidebarInstanceItem(info));
        }
    }

    private RPGMountEntity getPreviewEntity(String templateId) {
        if (previewEntity == null && this.minecraft != null && this.minecraft.level != null) {
            previewEntity = new RPGMountEntity(ddraig.net.rpgmounts.registry.ModEntities.RPG_MOUNT.get(), this.minecraft.level);
        }
        if (previewEntity != null) {
            previewEntity.setTemplateId(templateId);
        }
        return previewEntity;
    }

    private RPGMountEntity getActiveEntity(String instanceId) {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
            return null;
        }
        for (net.minecraft.world.entity.Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (entity instanceof RPGMountEntity mount) {
                if (instanceId.equals(mount.getInstanceId()) && this.minecraft.player.getUUID().equals(mount.getOwnerUuid())) {
                    return mount;
                }
            }
        }
        return null;
    }

    private boolean needsRefresh() {
        int cacheSize = RPGMountsClient.unlockedMounts.size();
        int listSize = groundMounts.size() + aquaticMounts.size() + flyingMounts.size();
        if (cacheSize != listSize) {
            return true;
        }
        for (RPGMountsClient.UnlockedMountInfo info : groundMounts) {
            if (!RPGMountsClient.unlockedMounts.containsKey(info.instanceId)) return true;
        }
        for (RPGMountsClient.UnlockedMountInfo info : aquaticMounts) {
            if (!RPGMountsClient.unlockedMounts.containsKey(info.instanceId)) return true;
        }
        for (RPGMountsClient.UnlockedMountInfo info : flyingMounts) {
            if (!RPGMountsClient.unlockedMounts.containsKey(info.instanceId)) return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (needsRefresh()) {
            this.init(this.minecraft, this.width, this.height);
        }

        // Dynamic 85% Panel Scaling
        int panelW = Math.max(340, Math.min(440, (int)(this.width * 0.85)));
        int panelH = Math.max(200, Math.min(280, (int)(this.height * 0.85)));
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        if (Minecraft.getInstance().screen == this) {
            this.renderBackground(graphics);
            int borderC = RPGWaypointsIntegration.getThemeColor("panelBorder", 0xFFDFD0A0);
            int bgC = RPGWaypointsIntegration.getThemeColor("panelBg", 0xFF2D2D2D);
            UIHelper.drawBeveledPanel(graphics, left, top, panelW, panelH, borderC, bgC);
        }

        hoveredTooltip = null;

        renderInTab(graphics, mouseX, mouseY, partialTicks, left, top, panelW, panelH);

        super.render(graphics, mouseX, mouseY, partialTicks);

        if (hoveredTooltip != null && Minecraft.getInstance().screen == this) {
            graphics.renderComponentTooltip(this.font, hoveredTooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelW = Math.max(340, Math.min(440, (int)(this.width * 0.85)));
        int panelH = Math.max(200, Math.min(280, (int)(this.height * 0.85)));
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        if (mouseClickedInTab(mouseX, mouseY, button, left, top, panelW, panelH)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void renderInTab(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, int x, int y, int width, int height) {
        int panelBorder = RPGWaypointsIntegration.getThemeColor("panelBorder", 0xFFDFD0A0);
        int slotBg = RPGWaypointsIntegration.getThemeColor("slotBg", 0xFF1C1C1C);
        int textColor = RPGWaypointsIntegration.getThemeColor("textColor", 0xFFCCCCCC);
        int textActiveColor = RPGWaypointsIntegration.getThemeColor("textActiveColor", 0xFFD4AF37);

        if (Minecraft.getInstance().screen == this) {
            graphics.drawString(this.font, this.title, x + 10, y - 14, textActiveColor, false);
        }

        // Left Column (Sidebar lists templates)
        int listX = x + 8;
        int listY = y + 8;
        int listWidth = (int)(width * 0.32);
        int listHeight = height - 16;

        UIHelper.drawRecessedSlot(graphics, listX, listY, listWidth, listHeight, panelBorder, slotBg);

        int listRowH = 11;
        int listVisibleRows = (listHeight - 4) / listRowH;
        int listMaxVisible = Math.min(listScrollOffset + listVisibleRows, sidebarItems.size());

        double scaleVal = this.minecraft.getWindow().getGuiScale();
        int listScissorX = (int) (listX * scaleVal);
        int listScissorY = (int) ((this.minecraft.getWindow().getGuiScaledHeight() - (listY + listHeight - 1)) * scaleVal);
        int listScissorW = (int) (listWidth * scaleVal);
        int listScissorH = (int) ((listHeight - 2) * scaleVal);

        com.mojang.blaze3d.systems.RenderSystem.enableScissor(listScissorX, listScissorY, listScissorW, listScissorH);
        int drawItemY = listY + 3;
        for (int i = listScrollOffset; i < listMaxVisible; i++) {
            SidebarInstanceItem item = sidebarItems.get(i);
            int drawY = drawItemY + (i - listScrollOffset) * listRowH;

            if (item.isHeader) {
                int col = UIHelper.getCategoryColor(item.category);
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.category." + item.category.toLowerCase()).getString(), listX + 4, drawY + 1, col, false);
            } else {
                RPGMountsClient.UnlockedMountInfo info = item.instance;
                MountData m = MountRegistry.getTemplate(info.mountId);
                if (m != null) {
                    int col = (info == selectedInstance) ? textActiveColor : textColor;
                    String displayName = (info.customName != null && !info.customName.isEmpty()) ? info.customName : m.name;

                    if (info == selectedInstance) {
                        graphics.fill(listX + 2, drawY, listX + listWidth - (sidebarItems.size() > listVisibleRows ? 8 : 2), drawY + listRowH, 0x40FFFFFF);
                    }

                    if (mouseX >= listX + 2 && mouseX <= listX + listWidth - (sidebarItems.size() > listVisibleRows ? 8 : 2) && mouseY >= drawY && mouseY <= drawY + listRowH - 1) {
                        graphics.fill(listX + 2, drawY, listX + listWidth - (sidebarItems.size() > listVisibleRows ? 8 : 2), drawY + listRowH, 0x20FFFFFF);
                    }

                    graphics.drawString(this.font, truncate(displayName, (listWidth - 14) / 6), listX + 8, drawY + 1, col, false);
                }
            }
        }
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();

        if (sidebarItems.size() > listVisibleRows) {
            int scrollbarX = listX + listWidth - 6;
            int scrollbarY = listY + 2;
            int scrollbarW = 4;
            int scrollbarH = listHeight - 4;
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0x40000000);

            int thumbH = Math.max(10, scrollbarH * listVisibleRows / sidebarItems.size());
            int thumbY = scrollbarY + (scrollbarH - thumbH) * listScrollOffset / (sidebarItems.size() - listVisibleRows);
            graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, panelBorder);
        }

        // Middle Column (Viewport & dynamic description)
        int viewX = listX + listWidth + 8;
        int viewY = y + 8;
        int viewWidth = (int)(width * 0.38);
        int viewHeight = (int)(height * 0.45);

        // Right Column (Stats panel)
        int statsX = viewX + viewWidth + 8;
        int statsW = width - (statsX - x) - 8;
        int statsY = y + 8;
        int statsH = height - 16;

        if (selectedMount != null) {
            int previewCenterX = viewX + viewWidth / 2;
            int previewCenterY = viewY + 65;

            boolean isLeftClickPressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (isLeftClickPressed) {
                if (!this.isDragging) {
                    if (mouseX >= viewX && mouseX <= viewX + viewWidth && mouseY >= viewY && mouseY <= viewY + viewHeight) {
                        this.isDragging = true;
                        this.lastMouseX = mouseX;
                    }
                } else {
                    double deltaX = mouseX - this.lastMouseX;
                    this.previewRotation -= deltaX * 1.5f;
                    this.lastMouseX = mouseX;
                }
            } else {
                this.isDragging = false;
                this.previewRotation += 0.5f;
            }

            // Viewport background recessed slot with category border
            int categoryBorderColor = UIHelper.getCategoryColor(selectedMount.category);
            UIHelper.drawRecessedSlot(graphics, viewX, viewY, viewWidth, viewHeight, categoryBorderColor, 0xFF12131A);

            RPGMountEntity dummy = getPreviewEntity(selectedMount.id);
            if (dummy != null) {
                dummy.setYRot(previewRotation);
                dummy.setYHeadRot(previewRotation);
                dummy.setXRot(0.0f);
                dummy.refreshDimensions();

                float baseScale = Math.min((viewHeight * 0.70f) / dummy.getBbHeight(), (viewWidth * 0.70f) / dummy.getBbWidth());
                if (baseScale < 0.2F) {
                    baseScale = 0.2F;
                }
                float defZoom = (selectedMount != null && selectedMount.previewZoom > 0.05f) ? selectedMount.previewZoom : 1.0f;
                int scaleFactor = (int) (baseScale * previewZoom * defZoom);
                float defOffsetY = selectedMount != null ? selectedMount.previewOffsetY : 0.0f;
                int centeredY = (viewY + viewHeight / 2) + (int) ((dummy.getBbHeight() * scaleFactor) / 2) + (int) (defOffsetY * scaleFactor);
                
                int viewScissorX = (int) (viewX * scaleVal);
                int viewScissorY = (int) ((this.minecraft.getWindow().getGuiScaledHeight() - (viewY + viewHeight - 1)) * scaleVal);
                int viewScissorW = (int) (viewWidth * scaleVal);
                int viewScissorH = (int) ((viewHeight - 2) * scaleVal);
                
                com.mojang.blaze3d.systems.RenderSystem.enableScissor(viewScissorX, viewScissorY, viewScissorW, viewScissorH);
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        graphics,
                        previewCenterX,
                        centeredY,
                        scaleFactor,
                        -30.0f,
                        -20.0f,
                        dummy
                );
                com.mojang.blaze3d.systems.RenderSystem.disableScissor();

                // Zoom Buttons
                int btnY = viewY + viewHeight - 14;
                int btnMinusX = viewX + viewWidth - 27;
                int btnPlusX = viewX + viewWidth - 14;

                boolean hoverMinus = mouseX >= btnMinusX && mouseX < btnMinusX + 12 && mouseY >= btnY && mouseY < btnY + 12;
                UIHelper.drawShadedButton(graphics, btnMinusX, btnY, 12, 12, hoverMinus, 0xFF3D2C1E);
                graphics.drawString(this.font, "-", btnMinusX + 4, btnY + 2, 0xFFFFFFFF, false);

                boolean hoverPlus = mouseX >= btnPlusX && mouseX < btnPlusX + 12 && mouseY >= btnY && mouseY < btnY + 12;
                UIHelper.drawShadedButton(graphics, btnPlusX, btnY, 12, 12, hoverPlus, 0xFF3D2C1E);
                graphics.drawString(this.font, "+", btnPlusX + 3, btnY + 2, 0xFFFFFFFF, false);
            }

            RPGMountEntity activeEntity = getActiveEntity(selectedInstance.instanceId);

            double maxHp = selectedMount.stats.maxHealth;
            double speedVal = selectedMount.stats.movementSpeed;
            double staminaVal = selectedMount.stats.maxStamina;
            int bondingVal = 0;

            if (selectedInstance != null) {
                bondingVal = selectedInstance.bondingScore;
            }

            if (activeEntity != null) {
            maxHp = activeEntity.getMaxHealth();
                speedVal = activeEntity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                bondingVal = activeEntity.getBonding();
            }

            // Draw Name & Rarity & Favorite Star below viewport in Middle Column
            String displayName = (selectedInstance.customName != null && !selectedInstance.customName.isEmpty()) ? selectedInstance.customName : selectedMount.name;
            int nameColor = UIHelper.getCategoryColor(selectedMount.category);
            int nameY = viewY + viewHeight + 6;
            graphics.drawString(this.font, displayName, viewX + 4, nameY, nameColor, false);

            boolean isFav = selectedInstance.instanceId.equals(ddraig.net.rpgmounts.client.RPGMountsClient.favoriteInstanceId);
            String starSymbol = isFav ? "★ [Fav]" : "☆ [Fav]";
            int starColor = isFav ? 0xFFFFD700 : 0xFF888888;
            int starX = viewX + viewWidth - this.font.width(starSymbol) - 2;
            graphics.drawString(this.font, starSymbol, starX, nameY, starColor, false);
            
            boolean showRarity = ModConfig.get().general.enableRarity;
            int rarityY = nameY + 11;
            if (showRarity && selectedMount.rarity != null) {
                String rarityVal = selectedMount.rarity.toUpperCase();
                int rarityColor = UIHelper.getRarityColor(rarityVal);
                String rarityLocalized = Component.translatable("gui.rpg_mounts.rarity." + rarityVal.toLowerCase()).getString();
                graphics.drawString(this.font, rarityLocalized, viewX + 4, rarityY, rarityColor, false);
            }

            // Wrapping description in Middle Column
            int descY = (showRarity && selectedMount.rarity != null) ? rarityY + 11 : nameY + 11;
            int descMaxH = height - (descY - y) - 10;
            String desc = selectedMount.description;
            if (desc.isEmpty()) {
                desc = Component.translatable("gui.rpg_mounts.bestiary.default_desc").getString();
            }
            Component descComp = Component.literal(desc).withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC);

            int descScissorX = (int) (viewX * scaleVal);
            int descScissorY = (int) ((this.minecraft.getWindow().getGuiScaledHeight() - (descY + descMaxH)) * scaleVal);
            int descScissorW = (int) (viewWidth * scaleVal);
            int descScissorH = (int) (descMaxH * scaleVal);

            com.mojang.blaze3d.systems.RenderSystem.enableScissor(descScissorX, descScissorY, descScissorW, descScissorH);
            graphics.drawWordWrap(this.font, descComp, viewX + 4, descY, viewWidth - 8, 0xFFFFFFFF);
            com.mojang.blaze3d.systems.RenderSystem.disableScissor();

            double currentHp = activeEntity != null ? activeEntity.getHealth() : maxHp;
            double currentStamina = activeEntity != null ? activeEntity.getStamina() : staminaVal;

            // Render Stats panel on the right side
            UIHelper.drawRecessedSlot(graphics, statsX, statsY, statsW, statsH, panelBorder, slotBg);

            int statsTabX = statsX + 8;
            int statsTabY = statsY + 6;
            String statsText = Component.translatable("gui.rpg_mounts.creator.tab.stats").getString();
            String ancestryText = Component.translatable("gui.rpg_mounts.hud.ancestry").getString();
            
            int statsTabW = this.font.width(statsText) + 12;
            int statsTabH = 10;
            boolean statsActive = activeTab.equals("STATS");
            int statsBorder = statsActive ? textActiveColor : 0xFF666666;
            int statsBg = statsActive ? 0xFF333333 : 0x40000000;
            graphics.fill(statsTabX, statsTabY, statsTabX + statsTabW, statsTabY + statsTabH, statsBg);
            UIHelper.drawOutline(graphics, statsTabX, statsTabY, statsTabW, statsTabH, statsBorder);
            graphics.drawString(this.font, statsText, statsTabX + 6, statsTabY + 1, statsActive ? 0xFFFFFFFF : 0xFF888888, false);

            int ancTabX = statsTabX + statsTabW + 4;
            int ancTabY = statsY + 6;
            int maxAncRight = statsX + statsW - 60;
            int ancTabW = Math.max(20, Math.min(this.font.width(ancestryText) + 12, maxAncRight - ancTabX));
            int ancTabH = 10;
            boolean ancActive = activeTab.equals("ANCESTRY");
            int ancBorder = ancActive ? textActiveColor : 0xFF666666;
            int ancBg = ancActive ? 0xFF333333 : 0x40000000;
            graphics.fill(ancTabX, ancTabY, ancTabX + ancTabW, ancTabY + ancTabH, ancBg);
            UIHelper.drawOutline(graphics, ancTabX, ancTabY, ancTabW, ancTabH, ancBorder);
            String displayAncestry = this.font.plainSubstrByWidth(ancestryText, Math.max(10, ancTabW - 8));
            graphics.drawString(this.font, displayAncestry, ancTabX + 4, ancTabY + 1, ancActive ? 0xFFFFFFFF : 0xFF888888, false);

            // Determine if evolution exists
            boolean hasUnlockedPath = false;
            if (ddraig.net.rpgmounts.api.EvolutionAPI.hasCustomProvider() && activeEntity != null) {
                List<ddraig.net.rpgmounts.api.IEvolutionProvider.EvolutionPathInfo> paths = 
                    ddraig.net.rpgmounts.api.EvolutionAPI.getProvider().getEvolutionPaths(activeEntity);
                for (ddraig.net.rpgmounts.api.IEvolutionProvider.EvolutionPathInfo path : paths) {
                    if (path.isUnlocked) {
                        hasUnlockedPath = true;
                        break;
                    }
                }
            }

            int actionAreaH = hasUnlockedPath ? 32 : 18;

            int contentX = statsX + 6;
            int contentY = statsY + 20;
            int contentW = statsW - 12;
            int contentH = statsH - 20 - actionAreaH - 4;

            int contentScissorX = (int) (contentX * scaleVal);
            int contentScissorY = (int) ((this.minecraft.getWindow().getGuiScaledHeight() - (contentY + contentH)) * scaleVal);
            int contentScissorW = (int) (contentW * scaleVal);
            int contentScissorH = (int) (contentH * scaleVal);

            com.mojang.blaze3d.systems.RenderSystem.enableScissor(contentScissorX, contentScissorY, contentScissorW, contentScissorH);

            if (activeTab.equals("STATS")) {
                int drawY = contentY - (int) statsScrollAmount;

                drawStatRow(graphics, Component.translatable("gui.rpg_mounts.hud.stat.hp").getString(), (int)currentHp + "/" + (int)maxHp, contentX, drawY, contentW, contentY, contentH, mouseX, mouseY, "gui.rpg_mounts.stats.hp.tooltip");
                drawY += 11;
                
                drawStatRow(graphics, Component.translatable("gui.rpg_mounts.hud.stat.stm").getString(), (int)currentStamina + "/" + (int)staminaVal, contentX, drawY, contentW, contentY, contentH, mouseX, mouseY, "gui.rpg_mounts.stats.stm.tooltip");
                drawY += 11;

                int levelVal = 1;
                float xpVal = 0.0f;
                if (activeEntity != null) {
                    levelVal = activeEntity.getLevel();
                    xpVal = activeEntity.getXp();
                } else if (selectedInstance != null) {
                    levelVal = selectedInstance.level;
                    xpVal = selectedInstance.xp;
                }
                drawStatRow(graphics, Component.translatable("gui.rpg_mounts.hud.stat.lvl").getString(), String.valueOf(levelVal), contentX, drawY, contentW, contentY, contentH, mouseX, mouseY, "gui.rpg_mounts.stats.lvl.tooltip");
                drawY += 11;
                
                drawStatRow(graphics, Component.translatable("gui.rpg_mounts.hud.stat.speed").getString(), String.format("%.0f", speedVal * 100), contentX, drawY, contentW, contentY, contentH, mouseX, mouseY, "gui.rpg_mounts.stats.speed.tooltip");
                drawY += 11;
                
                double swimSpeedVal = selectedMount.stats.swimSpeed;
                drawStatRow(graphics, Component.translatable("gui.rpg_mounts.hud.stat.swim_speed").getString(), String.format("%.0f", swimSpeedVal * 100), contentX, drawY, contentW, contentY, contentH, mouseX, mouseY, "gui.rpg_mounts.stats.swim_speed.tooltip");
                drawY += 11;
                
                double flySpeedVal = selectedMount.stats.flySpeed;
                drawStatRow(graphics, Component.translatable("gui.rpg_mounts.hud.stat.fly_speed").getString(), String.format("%.0f", flySpeedVal * 100), contentX, drawY, contentW, contentY, contentH, mouseX, mouseY, "gui.rpg_mounts.stats.fly_speed.tooltip");
                drawY += 11;
                
                double jumpHeightVal = selectedMount.stats.jumpHeight;
                drawStatRow(graphics, Component.translatable("gui.rpg_mounts.hud.stat.jump_height").getString(), String.format("%.2f", jumpHeightVal), contentX, drawY, contentW, contentY, contentH, mouseX, mouseY, "gui.rpg_mounts.stats.jump_height.tooltip");
                drawY += 11;
                
                double recoveryVal = selectedMount.stats.staminaRecoveryRate;
                drawStatRow(graphics, Component.translatable("gui.rpg_mounts.hud.stat.recovery").getString(), String.format("%.1f/s", recoveryVal), contentX, drawY, contentW, contentY, contentH, mouseX, mouseY, "gui.rpg_mounts.stats.recovery.tooltip");
                drawY += 11;
                
                drawStatRow(graphics, Component.translatable("gui.rpg_mounts.hud.stat.bond").getString(), bondingVal + "%", contentX, drawY, contentW, contentY, contentH, mouseX, mouseY, "gui.rpg_mounts.stats.bond.tooltip");
                drawY += 11;

                double dmgDealt = 0.0;
                double dmgTaken = 0.0;
                int hpZeroCount = 0;
                double distTravelled = 0.0;
                RPGMountsClient.UnlockedMountInfo uInfo = selectedInstance;
                if (uInfo != null) {
                    dmgDealt = uInfo.damageDealt;
                    dmgTaken = uInfo.damageTaken;
                    hpZeroCount = uInfo.hpZeroCount;
                    distTravelled = uInfo.distanceTravelled;
                }

                drawStatRow(graphics, Component.translatable("gui.rpg_mounts.hud.stat.dmg_dealt").getString(), String.format("%.1f", dmgDealt), contentX, drawY, contentW, contentY, contentH, mouseX, mouseY, "gui.rpg_mounts.stats.dmg_dealt.tooltip");
                drawY += 11;

                drawStatRow(graphics, Component.translatable("gui.rpg_mounts.hud.stat.dmg_taken").getString(), String.format("%.1f", dmgTaken), contentX, drawY, contentW, contentY, contentH, mouseX, mouseY, "gui.rpg_mounts.stats.dmg_taken.tooltip");
                drawY += 11;

                drawStatRow(graphics, Component.translatable("gui.rpg_mounts.hud.stat.hp_zero").getString(), String.valueOf(hpZeroCount), contentX, drawY, contentW, contentY, contentH, mouseX, mouseY, "gui.rpg_mounts.stats.hp_zero.tooltip");
                drawY += 11;

                drawStatRow(graphics, Component.translatable("gui.rpg_mounts.hud.stat.distance").getString(), String.format("%.0fm", distTravelled), contentX, drawY, contentW, contentY, contentH, mouseX, mouseY, "gui.rpg_mounts.stats.distance.tooltip");
                drawY += 11;
                
                int slot1Idx = activeEntity != null ? activeEntity.getAbility1Index() : 0;
                int slot2Idx = activeEntity != null ? activeEntity.getAbility2Index() : 1;
                String a1Name = Component.translatable("gui.rpg_mounts.hud.stat.none").getString();
                String a2Name = Component.translatable("gui.rpg_mounts.hud.stat.none").getString();
                List<MountData.AbilityData> allAb = (activeEntity != null) ? activeEntity.getAvailableAbilities() : selectedMount.availableAbilities;
                List<MountData.AbilityData> actives = new java.util.ArrayList<>();
                List<MountData.AbilityData> passives = new java.util.ArrayList<>();
                for (MountData.AbilityData ab : allAb) {
                    if (ab.isPassive) {
                        passives.add(ab);
                    } else {
                        actives.add(ab);
                    }
                }

                if (!actives.isEmpty()) {
                    if (slot1Idx >= 0 && slot1Idx < actives.size()) {
                        a1Name = actives.get(slot1Idx).name;
                    }
                    if (slot2Idx >= 0 && slot2Idx < actives.size()) {
                        a2Name = actives.get(slot2Idx).name;
                    }
                }
                drawStatRow(graphics, Component.translatable("gui.rpg_mounts.hud.stat.slots").getString(), truncate(a1Name, 8) + " / " + truncate(a2Name, 8), contentX, drawY, contentW, contentY, contentH, mouseX, mouseY, "gui.rpg_mounts.stats.slots.tooltip");
                drawY += 11;

                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.hud.actives").getString(), contentX, drawY + 2, textColor, false);
                int actLabelW = this.font.width(Component.translatable("gui.rpg_mounts.hud.actives").getString());
                int actRowX = contentX + Math.max(64, actLabelW + 6);
                int actRowY = drawY;

                for (int i = 0; i < actives.size(); i++) {
                    MountData.AbilityData ab = actives.get(i);
                    int itemBoxX = actRowX + i * 18;
                    boolean restricted = isAbilityRestricted(ab, selectedMount);
                    int borderCol = restricted ? 0xFFFF0000 : 0xFF666666;
                    int bgCol = restricted ? 0x60800000 : 0xFF333333;
                    
                    if (actRowY + 16 >= contentY && actRowY <= contentY + contentH) {
                        graphics.fill(itemBoxX, actRowY, itemBoxX + 16, actRowY + 16, bgCol);
                        UIHelper.drawOutline(graphics, itemBoxX, actRowY, 16, 16, borderCol);
                        String initial = ab.name.substring(0, Math.min(2, ab.name.length())).toUpperCase();
                        graphics.drawString(this.font, initial, itemBoxX + 3, actRowY + 4, restricted ? 0xFF888888 : 0xFFFFFFFF, false);
                    }

                    if (mouseX >= itemBoxX && mouseX <= itemBoxX + 16 && mouseY >= actRowY && mouseY <= actRowY + 16) {
                        if (mouseY >= contentY && mouseY <= contentY + contentH) {
                            List<Component> tooltip = new ArrayList<>();
                            tooltip.add(Component.literal("§6" + ab.name));
                            tooltip.add(Component.translatable("gui.rpg_mounts.hud.ability.type", ab.type).withStyle(net.minecraft.ChatFormatting.YELLOW));
                            tooltip.add(Component.translatable("gui.rpg_mounts.hud.ability.damage", ab.damage, ab.damageType).withStyle(net.minecraft.ChatFormatting.YELLOW));
                            tooltip.add(Component.translatable("gui.rpg_mounts.hud.ability.cooldown", (ab.cooldownTicks / 20.0)).withStyle(net.minecraft.ChatFormatting.YELLOW));
                            tooltip.add(Component.translatable("gui.rpg_mounts.hud.ability.stamina_cost", ab.staminaCost).withStyle(net.minecraft.ChatFormatting.YELLOW));
                            tooltip.add(Component.translatable("gui.rpg_mounts.hud.ability.range", ab.range).withStyle(net.minecraft.ChatFormatting.YELLOW));
                            if (!ab.description.isEmpty()) {
                                tooltip.add(Component.literal("§7" + ab.description));
                            }
                            if (restricted) {
                                tooltip.add(Component.translatable("gui.rpg_mounts.hud.restricted").withStyle(net.minecraft.ChatFormatting.RED));
                            } else {
                                tooltip.add(Component.translatable("gui.rpg_mounts.hud.click_sets").withStyle(net.minecraft.ChatFormatting.GREEN));
                            }
                            hoveredTooltip = tooltip;
                        }
                    }
                }
                drawY += 18;

                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.hud.passives").getString(), contentX, drawY + 2, textColor, false);
                int passLabelW = this.font.width(Component.translatable("gui.rpg_mounts.hud.passives").getString());
                int passRowX = contentX + Math.max(64, passLabelW + 6);
                int passRowY = drawY;

                for (int i = 0; i < passives.size(); i++) {
                    MountData.AbilityData ab = passives.get(i);
                    int itemBoxX = passRowX + i * 18;
                    boolean enabled = true;
                    if (activeEntity != null) {
                        enabled = activeEntity.isPassiveActive(ab.name);
                    }

                    int borderCol = enabled ? 0xFF00FF00 : 0xFF666666;
                    int bgCol = enabled ? 0x60008000 : 0xFF333333;
                    
                    if (passRowY + 16 >= contentY && passRowY <= contentY + contentH) {
                        graphics.fill(itemBoxX, passRowY, itemBoxX + 16, passRowY + 16, bgCol);
                        UIHelper.drawOutline(graphics, itemBoxX, passRowY, 16, 16, borderCol);
                        String initial = ab.name.substring(0, Math.min(2, ab.name.length())).toUpperCase();
                        graphics.drawString(this.font, initial, itemBoxX + 3, passRowY + 4, 0xFFFFFFFF, false);
                    }

                    if (mouseX >= itemBoxX && mouseX <= itemBoxX + 16 && mouseY >= passRowY && mouseY <= passRowY + 16) {
                        if (mouseY >= contentY && mouseY <= contentY + contentH) {
                            List<Component> tooltip = new ArrayList<>();
                            tooltip.add(Component.translatable("gui.rpg_mounts.hud.passive_suffix", ab.name).withStyle(net.minecraft.ChatFormatting.AQUA));
                            tooltip.add(Component.translatable(enabled ? "gui.rpg_mounts.hud.status_on" : "gui.rpg_mounts.hud.status_off").withStyle(enabled ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
                            if (!ab.description.isEmpty()) {
                                tooltip.add(Component.literal("§7" + ab.description));
                            }
                            if (activeEntity != null) {
                                tooltip.add(Component.translatable("gui.rpg_mounts.hud.click_toggle").withStyle(net.minecraft.ChatFormatting.GREEN));
                            } else {
                                tooltip.add(Component.translatable("gui.rpg_mounts.hud.summon_to_toggle").withStyle(net.minecraft.ChatFormatting.RED));
                            }
                            hoveredTooltip = tooltip;
                        }
                    }
                }
                drawY += 18;
            } else if (activeTab.equals("ANCESTRY")) {
                int drawY = contentY - (int) ancestryScrollAmount;
                List<String> lines = getAncestryLines();
                for (String line : lines) {
                    if (drawY + 10 >= contentY && drawY <= contentY + contentH) {
                        graphics.drawString(this.font, line, contentX, drawY, textColor, false);
                    }
                    drawY += 11;
                }
            }

            com.mojang.blaze3d.systems.RenderSystem.disableScissor();

            int totalHeight = activeTab.equals("STATS") ? getStatsTotalHeight() : getAncestryTotalHeight();
            double currentScroll = activeTab.equals("STATS") ? statsScrollAmount : ancestryScrollAmount;
            if (totalHeight > contentH) {
                int scrollbarX = contentX + contentW - 4;
                int scrollbarY = contentY;
                int scrollbarH = contentH;
                
                double ratio = (double) contentH / totalHeight;
                int trackHeight = (int) (scrollbarH * ratio);
                int trackY = scrollbarY + (int) (currentScroll * ratio);
                
                graphics.fill(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarH, 0x40000000);
                graphics.fill(scrollbarX, trackY, scrollbarX + 3, trackY + trackHeight, textActiveColor);
            }

            // Buttons Stack inside Right Column bottom
            int btnW = 50;
            int btnH = 12;
            int btnX = statsX + statsW - btnW - 6;

            int summonBtnY;
            if (hasUnlockedPath) {
                int evolveBtnY = statsY + statsH - btnH - 6;
                summonBtnY = evolveBtnY - btnH - 4;

                boolean hoverEvolve = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= evolveBtnY && mouseY <= evolveBtnY + btnH;
                UIHelper.drawShadedButton(graphics, btnX, evolveBtnY, btnW, btnH, hoverEvolve, 0xFF4A2B6A); // Purple Evolve
                String evolveLabel = Component.translatable("gui.rpg_mounts.hud.evolve").getString();
                graphics.drawString(this.font, evolveLabel, btnX + (btnW - this.font.width(evolveLabel)) / 2, evolveBtnY + 2, 0xFFFFFFFF, false);
            } else {
                summonBtnY = statsY + statsH - btnH - 6;
            }

            boolean isActive = activeEntity != null;
            int btnCol = isActive ? 0xFF990000 : 0xFF005500;
            String btnLabel = Component.translatable(isActive ? "gui.rpg_mounts.hud.dismiss" : "gui.rpg_mounts.hud.summon").getString();

            boolean hoverSummon = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= summonBtnY && mouseY <= summonBtnY + btnH;
            UIHelper.drawShadedButton(graphics, btnX, summonBtnY, btnW, btnH, hoverSummon, btnCol);
            graphics.drawString(this.font, btnLabel, btnX + (btnW - this.font.width(btnLabel)) / 2, summonBtnY + 2, 0xFFFFFFFF, false);

        } else {
            int centerX = statsX + statsW / 2;
            int centerY = statsY + statsH / 2;
            String text = Component.translatable("gui.rpg_mounts.hud.no_mounts").getString();
            graphics.drawCenteredString(this.font, text, centerX, centerY - 4, 0xFF888888);
        }
    }

    public boolean mouseClickedInTab(double mouseX, double mouseY, int button, int x, int y, int width, int height) {
        int listWidth = (int)(width * 0.32);
        int listX = x + 8;
        int listY = y + 8;
        int listHeight = height - 16;

        // Left sidebar selection click
        int listRowH = 11;
        int listVisibleRows = (listHeight - 4) / listRowH;
        int listMaxVisible = Math.min(listScrollOffset + listVisibleRows, sidebarItems.size());
        
        if (button == 0) {
            int drawItemY = listY + 3;
            for (int i = listScrollOffset; i < listMaxVisible; i++) {
                SidebarInstanceItem item = sidebarItems.get(i);
                int drawY = drawItemY + (i - listScrollOffset) * listRowH;
                if (!item.isHeader) {
                    if (mouseX >= listX + 2 && mouseX <= listX + listWidth - (sidebarItems.size() > listVisibleRows ? 8 : 2) && mouseY >= drawY && mouseY <= drawY + listRowH - 1) {
                        selectedInstance = item.instance;
                        selectedMount = MountRegistry.getTemplate(selectedInstance.mountId);
                        previewZoom = 1.0f;
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                }
            }
        }

        if (selectedInstance != null && selectedMount != null) {
            // Middle Column
            int viewX = listX + listWidth + 8;
            int viewY = y + 8;
            int viewWidth = (int)(width * 0.38);
            int viewHeight = (int)(height * 0.45);
            int btnY = viewY + viewHeight - 14;
            int btnMinusX = viewX + viewWidth - 27;
            int btnPlusX = viewX + viewWidth - 14;

            if (button == 0) {
                if (mouseX >= btnMinusX && mouseX < btnMinusX + 12 && mouseY >= btnY && mouseY < btnY + 12) {
                    previewZoom = Math.max(0.2f, previewZoom - 0.1f);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
                if (mouseX >= btnPlusX && mouseX < btnPlusX + 12 && mouseY >= btnY && mouseY < btnY + 12) {
                    previewZoom = Math.min(4.0f, previewZoom + 0.1f);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }

            // Right Column
            int statsX = viewX + viewWidth + 8;
            int statsW = width - (statsX - x) - 8;
            int statsY = y + 8;
            int statsH = height - 16;

            int nameY = viewY + viewHeight + 6;
            String starSymbol = "★ [Fav]";
            int starW = this.font.width(starSymbol) + 4;
            int starX = viewX + viewWidth - starW - 2;
            if (mouseX >= starX && mouseX <= starX + starW && mouseY >= nameY && mouseY <= nameY + 12) {
                if (selectedInstance.instanceId.equals(ddraig.net.rpgmounts.client.RPGMountsClient.favoriteInstanceId)) {
                    ddraig.net.rpgmounts.client.RPGMountsClient.favoriteInstanceId = "";
                } else {
                    ddraig.net.rpgmounts.client.RPGMountsClient.favoriteInstanceId = selectedInstance.instanceId;
                }
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            String statsText = Component.translatable("gui.rpg_mounts.creator.tab.stats").getString();
            String ancestryText = Component.translatable("gui.rpg_mounts.hud.ancestry").getString();
            
            int statsTabW = this.font.width(statsText) + 12;
            int statsTabX = statsX + 8;
            int statsTabY = statsY + 6;
            int statsTabH = 10;
            if (mouseX >= statsTabX && mouseX <= statsTabX + statsTabW && mouseY >= statsTabY && mouseY <= statsTabY + statsTabH) {
                activeTab = "STATS";
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            int ancTabX = statsTabX + statsTabW + 4;
            int ancTabY = statsY + 6;
            int maxAncRight = statsX + statsW - 60;
            int ancTabW = Math.max(20, Math.min(this.font.width(ancestryText) + 12, maxAncRight - ancTabX));
            int ancTabH = 10;
            if (mouseX >= ancTabX && mouseX <= ancTabX + ancTabW && mouseY >= ancTabY && mouseY <= ancTabY + ancTabH) {
                activeTab = "ANCESTRY";
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            int btnW = 50;
            int btnH = 12;
            int btnX = statsX + statsW - btnW - 6;

            RPGMountEntity activeEntity = getActiveEntity(selectedInstance.instanceId);

            boolean hasUnlockedPath = false;
            if (ddraig.net.rpgmounts.api.EvolutionAPI.hasCustomProvider() && activeEntity != null) {
                List<ddraig.net.rpgmounts.api.IEvolutionProvider.EvolutionPathInfo> paths = 
                    ddraig.net.rpgmounts.api.EvolutionAPI.getProvider().getEvolutionPaths(activeEntity);
                for (ddraig.net.rpgmounts.api.IEvolutionProvider.EvolutionPathInfo path : paths) {
                    if (path.isUnlocked) {
                        hasUnlockedPath = true;
                        break;
                    }
                }
            }

            int summonBtnY;
            if (hasUnlockedPath) {
                int evolveBtnY = statsY + statsH - btnH - 6;
                summonBtnY = evolveBtnY - btnH - 4;

                if (button == 0 && mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= evolveBtnY && mouseY <= evolveBtnY + btnH) {
                    ddraig.net.rpgmounts.api.EvolutionAPI.getProvider().openEvolutionScreen(activeEntity);
                    return true;
                }
            } else {
                summonBtnY = statsY + statsH - btnH - 6;
            }

            if (button == 0 && mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= summonBtnY && mouseY <= summonBtnY + btnH) {
                if (activeEntity != null) {
                    dev.architectury.networking.NetworkManager.sendToServer(ModPackets.C2S_DISMISS, new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()));
                } else {
                    FriendlyByteBuf summonBuf = new FriendlyByteBuf(Unpooled.buffer());
                    summonBuf.writeUtf(selectedInstance.instanceId);
                    NetworkManager.sendToServer(ModPackets.C2S_SUMMON, summonBuf);
                }
                if (Minecraft.getInstance().screen == this) {
                    Minecraft.getInstance().setScreen(null);
                }
                return true;
            }

            if (activeTab.equals("STATS") && activeEntity != null) {
                int actionAreaH = hasUnlockedPath ? 32 : 18;
                int contentY = statsY + 20;
                int contentH = statsH - 20 - actionAreaH - 4;
                
                int clickActRowY = contentY + 154 - (int) statsScrollAmount;
                if (mouseY >= contentY && mouseY <= contentY + contentH) {
                    int contentX = statsX + 6;
                    int actLabelW = this.font.width(Component.translatable("gui.rpg_mounts.hud.actives").getString());
                    int clickActRowX = contentX + Math.max(64, actLabelW + 6);
                    
                    List<MountData.AbilityData> allAb = activeEntity.getAvailableAbilities();
                    List<MountData.AbilityData> actives = new java.util.ArrayList<>();
                    List<MountData.AbilityData> passives = new java.util.ArrayList<>();
                    for (MountData.AbilityData ab : allAb) {
                        if (ab.isPassive) {
                            passives.add(ab);
                        } else {
                            actives.add(ab);
                        }
                    }

                    for (int i = 0; i < actives.size(); i++) {
                        MountData.AbilityData ab = actives.get(i);
                        int itemBoxX = clickActRowX + i * 18;
                        if (mouseX >= itemBoxX && mouseX <= itemBoxX + 16 && mouseY >= clickActRowY && mouseY <= clickActRowY + 16) {
                            if (!isAbilityRestricted(ab, selectedMount)) {
                                int allIdx = allAb.indexOf(ab);
                                if (allIdx >= 0) {
                                    int slot = (button == 1) ? 2 : 1;
                                    FriendlyByteBuf switchBuf = new FriendlyByteBuf(Unpooled.buffer());
                                    switchBuf.writeInt(activeEntity.getId());
                                    switchBuf.writeInt(slot);
                                    switchBuf.writeInt(allIdx);
                                    NetworkManager.sendToServer(ModPackets.C2S_SWITCH_ABILITY, switchBuf);
                                    return true;
                                }
                            }
                        }
                    }

                    int clickPassRowY = clickActRowY + 18;
                    int passLabelW = this.font.width(Component.translatable("gui.rpg_mounts.hud.passives").getString());
                    int clickPassRowX = contentX + Math.max(64, passLabelW + 6);
                    for (int i = 0; i < passives.size(); i++) {
                        MountData.AbilityData ab = passives.get(i);
                        int itemBoxX = clickPassRowX + i * 18;
                        if (mouseX >= itemBoxX && mouseX <= itemBoxX + 16 && mouseY >= clickPassRowY && mouseY <= clickPassRowY + 16) {
                            boolean currentEnabled = activeEntity.isPassiveActive(ab.name);
                            FriendlyByteBuf toggleBuf = new FriendlyByteBuf(Unpooled.buffer());
                            toggleBuf.writeInt(activeEntity.getId());
                            toggleBuf.writeUtf(ab.name);
                            toggleBuf.writeBoolean(!currentEnabled);
                            NetworkManager.sendToServer(ModPackets.C2S_TOGGLE_PASSIVE, toggleBuf);
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean isAbilityRestricted(MountData.AbilityData ability, MountData mount) {
        if (ability.allowedCategories != null && !ability.allowedCategories.isEmpty()) {
            if (!ability.allowedCategories.contains(mount.category.toUpperCase())) {
                return true;
            }
        }
        if (ability.allowedMountIds != null && !ability.allowedMountIds.isEmpty()) {
            if (!ability.allowedMountIds.contains(mount.id)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String text, int length) {
        if (text.length() <= length) return text;
        return text.substring(0, length - 2) + "..";
    }

    public boolean isInitialized() {
        return this.minecraft != null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int panelW = Math.max(340, Math.min(440, (int)(this.width * 0.85)));
        int panelH = Math.max(200, Math.min(280, (int)(this.height * 0.85)));
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;
        return mouseScrolledInTab(mouseX, mouseY, amount, left, top, panelW, panelH);
    }

    public boolean mouseScrolledInTab(double mouseX, double mouseY, double amount, int x, int y, int width, int height) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            int listWidth = (int)(width * 0.32);
            int listX = x + 8;
            int listY = y + 8;
            int listHeight = height - 16;
            
            // Left sidebar scrolling
            if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
                int listRowH = 11;
                int listVisibleRows = (listHeight - 4) / listRowH;
                int maxOffset = Math.max(0, sidebarItems.size() - listVisibleRows);
                if (amount > 0) {
                    listScrollOffset = Math.max(0, listScrollOffset - 1);
                } else if (amount < 0) {
                    listScrollOffset = Math.min(maxOffset, listScrollOffset + 1);
                }
                return true;
            }

            int viewWidth = (int)(width * 0.38);
            int statsX = listX + listWidth + 8 + viewWidth + 8;
            int statsW = width - (statsX - x) - 8;
            int statsY = y + 8;
            int statsH = height - 16;

            boolean hasUnlockedPath = false;
            RPGMountEntity activeEntity = getActiveEntity(selectedInstance != null ? selectedInstance.instanceId : "");
            if (ddraig.net.rpgmounts.api.EvolutionAPI.hasCustomProvider() && activeEntity != null) {
                List<ddraig.net.rpgmounts.api.IEvolutionProvider.EvolutionPathInfo> paths = 
                    ddraig.net.rpgmounts.api.EvolutionAPI.getProvider().getEvolutionPaths(activeEntity);
                for (ddraig.net.rpgmounts.api.IEvolutionProvider.EvolutionPathInfo path : paths) {
                    if (path.isUnlocked) {
                        hasUnlockedPath = true;
                        break;
                    }
                }
            }

            int actionAreaH = hasUnlockedPath ? 32 : 18;
            int contentY = statsY + 20;
            int contentH = statsH - 20 - actionAreaH - 4;
            
            if (mouseX >= statsX && mouseX <= statsX + statsW && mouseY >= contentY && mouseY <= contentY + contentH) {
                if (activeTab.equals("STATS")) {
                    int totalHeight = getStatsTotalHeight();
                    double maxScroll = Math.max(0, totalHeight - contentH);
                    this.statsScrollAmount = Mth.clamp(this.statsScrollAmount - amount * 10, 0, maxScroll);
                } else if (activeTab.equals("ANCESTRY")) {
                    int totalHeight = getAncestryTotalHeight();
                    double maxScroll = Math.max(0, totalHeight - contentH);
                    this.ancestryScrollAmount = Mth.clamp(this.ancestryScrollAmount - amount * 10, 0, maxScroll);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.previewEntity != null) {
            this.previewEntity.tickCount++;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
