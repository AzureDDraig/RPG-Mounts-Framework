package ddraig.net.rpgmounts.client.gui;

import ddraig.net.rpgmounts.client.RPGMountsClient;
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
import ddraig.net.rpgmounts.config.ModConfig;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * RPG Mounts Bestiary Screen
 * Premium obsidian dark-themed UI displaying all obtainable mounts,
 * with category headers, dynamic scrollbars, safe model auto-scaling,
 * stats displays, and summon/dismiss integrations.
 */
public class BestiaryScreen extends Screen {
    private final List<MountData> allMounts = new ArrayList<>();
    private final List<SidebarItem> sidebarItems = new ArrayList<>();
    private MountData selectedMount;
    private float previewRotation = 0.0f;
    private RPGMountEntity previewEntity;

    private boolean isDragging = false;
    private double lastMouseX = 0.0;
    private float previewZoom = 1.0f;
    private int scrollOffset = 0;

    private static class SidebarItem {
        final boolean isHeader;
        final String category;
        final MountData mount;

        SidebarItem(String category) {
            this.isHeader = true;
            this.category = category;
            this.mount = null;
        }

        SidebarItem(MountData mount) {
            this.isHeader = false;
            this.category = null;
            this.mount = mount;
        }
    }

    public BestiaryScreen() {
        super(Component.translatable("gui.rpg_mounts.bestiary.title"));
    }

    private boolean isMountDiscovered(String mountId) {
        if (RPGMountsClient.discoveredMounts.contains(mountId)) {
            return true;
        }
        for (RPGMountsClient.UnlockedMountInfo info : RPGMountsClient.unlockedMounts.values()) {
            if (mountId.equals(info.mountId)) {
                return true;
            }
        }
        return false;
    }

    private RPGMountsClient.UnlockedMountInfo getOwnedInstance(String templateId) {
        for (RPGMountsClient.UnlockedMountInfo info : RPGMountsClient.unlockedMounts.values()) {
            if (templateId.equals(info.mountId)) {
                return info;
            }
        }
        return null;
    }

    private RPGMountEntity getActiveEntityOfTemplate(String templateId) {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
            return null;
        }
        for (net.minecraft.world.entity.Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (entity instanceof RPGMountEntity mount) {
                if (templateId.equals(mount.getTemplateId()) && this.minecraft.player.getUUID().equals(mount.getOwnerUuid())) {
                    return mount;
                }
            }
        }
        return null;
    }

    @Override
    protected void init() {
        allMounts.clear();
        allMounts.addAll(MountRegistry.loadedTemplates.values());

        // Find first discovered mount to select by default, or first one if none
        selectedMount = null;
        for (MountData m : allMounts) {
            if (isMountDiscovered(m.id)) {
                selectedMount = m;
                break;
            }
        }
        if (selectedMount == null && !allMounts.isEmpty()) {
            selectedMount = allMounts.get(0);
        }

        // Build categorized list items
        sidebarItems.clear();

        // GROUND
        boolean firstGround = true;
        for (MountData m : allMounts) {
            if (m.category == null || m.category.equalsIgnoreCase("GROUND")) {
                if (firstGround) {
                    sidebarItems.add(new SidebarItem("GROUND"));
                    firstGround = false;
                }
                sidebarItems.add(new SidebarItem(m));
            }
        }
        // AQUATIC
        boolean firstAquatic = true;
        for (MountData m : allMounts) {
            if (m.category != null && m.category.equalsIgnoreCase("AQUATIC")) {
                if (firstAquatic) {
                    sidebarItems.add(new SidebarItem("AQUATIC"));
                    firstAquatic = false;
                }
                sidebarItems.add(new SidebarItem(m));
            }
        }
        // FLYING
        boolean firstFlying = true;
        for (MountData m : allMounts) {
            if (m.category != null && m.category.equalsIgnoreCase("FLYING")) {
                if (firstFlying) {
                    sidebarItems.add(new SidebarItem("FLYING"));
                    firstFlying = false;
                }
                sidebarItems.add(new SidebarItem(m));
            }
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        // Dynamic 85% Panel Scaling
        int panelW = Math.max(340, Math.min(440, (int)(this.width * 0.85)));
        int panelH = Math.max(200, Math.min(280, (int)(this.height * 0.85)));
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        int borderC = RPGWaypointsIntegration.getThemeColor("panelBorder", 0xFFDFD0A0);
        int bgC = RPGWaypointsIntegration.getThemeColor("panelBg", 0xFF2D2D2D);

        // Main beveled obsidian panel
        UIHelper.drawBeveledPanel(graphics, left, top, panelW, panelH, borderC, bgC);

        // Sidebar dimensions
        int listX = left + 8;
        int listY = top + 8;
        int listW = (int)(panelW * 0.32);
        int listH = panelH - 16;

        // Viewport dimensions
        int viewX = listX + listW + 8;
        int viewY = top + 8;
        int viewW = (int)(panelW * 0.38);
        int viewH = (int)(panelH * 0.45);

        // Stats column dimensions
        int statsX = viewX + viewW + 8;
        int statsW = panelW - (statsX - left) - 8;
        int statsY = top + 8;
        int statsH = panelH - 16;

        // Draw Left sidebar recessed slot
        UIHelper.drawRecessedSlot(graphics, listX, listY, listW, listH, borderC, 0xFF1C1C1C);

        // Draw Left Sidebar list rows
        int rowH = 12;
        int visibleRows = (listH - 4) / rowH;
        int itemY = listY + 3;
        int maxVisible = Math.min(scrollOffset + visibleRows, sidebarItems.size());

        double scaleVal = this.minecraft.getWindow().getGuiScale();
        int scissorX = (int) (listX * scaleVal);
        int scissorY = (int) ((this.minecraft.getWindow().getGuiScaledHeight() - (listY + listH - 1)) * scaleVal);
        int scissorW = (int) (listW * scaleVal);
        int scissorH = (int) ((listH - 2) * scaleVal);

        com.mojang.blaze3d.systems.RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
        for (int i = scrollOffset; i < maxVisible; i++) {
            SidebarItem item = sidebarItems.get(i);
            int drawY = itemY + (i - scrollOffset) * rowH;

            if (item.isHeader) {
                int col = UIHelper.getCategoryColor(item.category);
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.category." + item.category.toLowerCase()).getString(), listX + 4, drawY + 2, col, false);
            } else {
                MountData m = item.mount;
                boolean isUnlocked = isMountDiscovered(m.id);
                String prefix = isUnlocked ? "§2✔ " : "§8❌ ";
                String displayName = prefix + (isUnlocked ? m.name : "???");
                int col = isUnlocked ? ((m == selectedMount) ? 0xFFFFFFFF : 0xFFCCCCCC) : ((m == selectedMount) ? 0xFF888888 : 0xFF555555);

                if (m == selectedMount) {
                    graphics.fill(listX + 2, drawY, listX + listW - (sidebarItems.size() > visibleRows ? 8 : 2), drawY + rowH, 0x40FFFFFF);
                }

                if (mouseX >= listX + 2 && mouseX <= listX + listW - (sidebarItems.size() > visibleRows ? 8 : 2) && mouseY >= drawY && mouseY <= drawY + rowH - 1) {
                    graphics.fill(listX + 2, drawY, listX + listW - (sidebarItems.size() > visibleRows ? 8 : 2), drawY + rowH, 0x20FFFFFF);
                }

                graphics.drawString(this.font, truncate(displayName, (listW - 14) / 6), listX + 8, drawY + 2, col, false);
            }
        }
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();

        // Draw Left sidebar scrollbar
        if (sidebarItems.size() > visibleRows) {
            int scrollbarX = listX + listW - 6;
            int scrollbarY = listY + 2;
            int scrollbarW = 4;
            int scrollbarH = listH - 4;
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0x40000000);

            int thumbH = Math.max(10, scrollbarH * visibleRows / sidebarItems.size());
            int thumbY = scrollbarY + (scrollbarH - thumbH) * scrollOffset / (sidebarItems.size() - visibleRows);
            graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, borderC);
        }

        // Draw 3D Viewport
        int viewportBorderColor = selectedMount != null ? UIHelper.getCategoryColor(selectedMount.category) : borderC;
        UIHelper.drawRecessedSlot(graphics, viewX, viewY, viewW, viewH, viewportBorderColor, 0xFF12131A);

        if (selectedMount != null) {
            boolean isUnlocked = isMountDiscovered(selectedMount.id);

            // Left Drag Rotation logic
            boolean isLeftClickPressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (isLeftClickPressed) {
                if (!this.isDragging) {
                    if (mouseX >= viewX && mouseX <= viewX + viewW && mouseY >= viewY && mouseY <= viewY + viewH) {
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

            RPGMountEntity dummy = getPreviewEntity(selectedMount.id);
            if (dummy != null) {
                dummy.setSilhouette(!isUnlocked);
                dummy.setYRot(previewRotation);
                dummy.setYHeadRot(previewRotation);
                dummy.setXRot(0.0f);
                dummy.refreshDimensions();

                float baseScale = Math.min((viewH * 0.70f) / dummy.getBbHeight(), (viewW * 0.70f) / dummy.getBbWidth());
                if (baseScale < 0.2F) {
                    baseScale = 0.2F;
                }
                int scaleFactor = (int) (baseScale * previewZoom);
                int centeredY = (viewY + viewH / 2) + (int) ((dummy.getBbHeight() * scaleFactor) / 2);

                int viewScissorX = (int) (viewX * scaleVal);
                int viewScissorY = (int) ((this.minecraft.getWindow().getGuiScaledHeight() - (viewY + viewH - 1)) * scaleVal);
                int viewScissorW = (int) (viewW * scaleVal);
                int viewScissorH = (int) ((viewH - 2) * scaleVal);

                com.mojang.blaze3d.systems.RenderSystem.enableScissor(viewScissorX, viewScissorY, viewScissorW, viewScissorH);
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        graphics,
                        viewX + viewW / 2,
                        centeredY,
                        scaleFactor,
                        -30.0f,
                        -20.0f,
                        dummy
                );
                com.mojang.blaze3d.systems.RenderSystem.disableScissor();

                // Viewport Zoom overlay buttons
                int btnY = viewY + viewH - 14;
                int btnMinusX = viewX + viewW - 27;
                int btnPlusX = viewX + viewW - 14;

                boolean hoverMinus = mouseX >= btnMinusX && mouseX < btnMinusX + 12 && mouseY >= btnY && mouseY < btnY + 12;
                UIHelper.drawShadedButton(graphics, btnMinusX, btnY, 12, 12, hoverMinus, 0xFF3D2C1E);
                graphics.drawString(this.font, "-", btnMinusX + 4, btnY + 2, 0xFFFFFFFF, false);

                boolean hoverPlus = mouseX >= btnPlusX && mouseX < btnPlusX + 12 && mouseY >= btnY && mouseY < btnY + 12;
                UIHelper.drawShadedButton(graphics, btnPlusX, btnY, 12, 12, hoverPlus, 0xFF3D2C1E);
                graphics.drawString(this.font, "+", btnPlusX + 3, btnY + 2, 0xFFFFFFFF, false);
            }

            // Draw Name & Rarity labels
            int nameY = viewY + viewH + 6;
            String nameText = isUnlocked ? selectedMount.name : "???";
            int nameColor = UIHelper.getCategoryColor(selectedMount.category);
            graphics.drawString(this.font, nameText, viewX + 4, nameY, nameColor, false);

            int rarityY = nameY + 11;
            boolean showRarity = ModConfig.get().general.enableRarity;
            if (showRarity && isUnlocked && selectedMount.rarity != null) {
                String rarityVal = selectedMount.rarity.toUpperCase();
                int rarityColor = UIHelper.getRarityColor(rarityVal);
                String localizedRarity = Component.translatable("gui.rpg_mounts.rarity." + rarityVal.toLowerCase()).getString();
                graphics.drawString(this.font, localizedRarity, viewX + 4, rarityY, rarityColor, false);
            }

            // Description wrapping below viewport
            int descY = showRarity && isUnlocked ? rarityY + 11 : nameY + 11;
            int descMaxH = panelH - (descY - top) - 10;

            Component descComp;
            if (isUnlocked) {
                String desc = selectedMount.description;
                if (desc.isEmpty()) {
                    descComp = Component.translatable("gui.rpg_mounts.bestiary.default_desc").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC);
                } else {
                    descComp = Component.literal(desc).withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC);
                }
            } else {
                descComp = Component.translatable("gui.rpg_mounts.bestiary.undiscovered").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC);
            }

            int descScissorX = (int) (viewX * scaleVal);
            int descScissorY = (int) ((this.minecraft.getWindow().getGuiScaledHeight() - (descY + descMaxH)) * scaleVal);
            int descScissorW = (int) (viewW * scaleVal);
            int descScissorH = (int) (descMaxH * scaleVal);

            com.mojang.blaze3d.systems.RenderSystem.enableScissor(descScissorX, descScissorY, descScissorW, descScissorH);
            List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(descComp, viewW - 8);
            int lineY = descY;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                graphics.drawString(this.font, line, viewX + 4, lineY, 0xFFCCCCCC, false);
                lineY += 10;
            }
            com.mojang.blaze3d.systems.RenderSystem.disableScissor();

            // Render stats column on the right side
            UIHelper.drawRecessedSlot(graphics, statsX, statsY, statsW, statsH, borderC, 0xFF1C1C1C);

            int labelColor = 0xFFCCCCCC;
            int valueColor = 0xFFD4AF37;
            int drawY = statsY + 6;

            graphics.drawString(this.font, "§e§l" + Component.translatable("gui.rpg_mounts.bestiary.stats_header").getString(), statsX + 6, drawY, 0xFFFFFFFF, false);
            drawY += 12;

            String catText = "???";
            if (isUnlocked && selectedMount.category != null) {
                catText = Component.translatable("gui.rpg_mounts.category." + selectedMount.category.toLowerCase()).getString();
            }
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.bestiary.category").getString(), statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, catText, statsX + 62, drawY, valueColor, false);
            drawY += 11;

            String hpText = isUnlocked ? String.valueOf((int)selectedMount.stats.maxHealth) : "???";
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.bestiary.health").getString(), statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, hpText, statsX + 62, drawY, valueColor, false);
            drawY += 11;

            String speedText = isUnlocked ? String.format("%.0f", selectedMount.stats.movementSpeed * 100) : "???";
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.bestiary.speed").getString(), statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, speedText, statsX + 62, drawY, valueColor, false);
            drawY += 11;

            String swimText = isUnlocked ? String.format("%.0f", selectedMount.stats.swimSpeed * 100) : "???";
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.bestiary.swim_speed").getString(), statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, swimText, statsX + 62, drawY, valueColor, false);
            drawY += 11;

            String flyText = isUnlocked ? String.format("%.0f", selectedMount.stats.flySpeed * 100) : "???";
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.bestiary.fly_speed").getString(), statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, flyText, statsX + 62, drawY, valueColor, false);
            drawY += 11;

            String jumpText = isUnlocked ? String.format("%.2f", selectedMount.stats.jumpHeight) : "???";
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.bestiary.jump_height").getString(), statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, jumpText, statsX + 62, drawY, valueColor, false);
            drawY += 11;

            String stamText = isUnlocked ? String.valueOf((int)selectedMount.stats.maxStamina) : "???";
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.bestiary.stamina").getString(), statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, stamText, statsX + 62, drawY, valueColor, false);
            drawY += 11;

            String recText = isUnlocked ? String.format("%.1f/s", selectedMount.stats.staminaRecoveryRate) : "???";
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.bestiary.recovery").getString(), statsX + 6, drawY, labelColor, false);
            graphics.drawString(this.font, recText, statsX + 62, drawY, valueColor, false);

            // Action Button
            int actBtnW = statsW - 12;
            int actBtnH = 14;
            int actBtnX = statsX + 6;
            int actBtnY = statsY + statsH - actBtnH - 6;

            RPGMountsClient.UnlockedMountInfo ownedInstance = getOwnedInstance(selectedMount.id);
            boolean isOwned = ownedInstance != null;
            boolean isActive = getActiveEntityOfTemplate(selectedMount.id) != null;

            boolean hoverBtn = mouseX >= actBtnX && mouseX <= actBtnX + actBtnW && mouseY >= actBtnY && mouseY <= actBtnY + actBtnH;

            if (!isOwned) {
                String labelText = Component.translatable("gui.rpg_mounts.bestiary.locked").getString();
                UIHelper.drawShadedButton(graphics, actBtnX, actBtnY, actBtnW, actBtnH, false, 0xFF3D3D3D);
                graphics.drawString(this.font, labelText, actBtnX + (actBtnW - this.font.width(labelText)) / 2, actBtnY + 3, 0xFF888888, false);
            } else if (isActive) {
                String labelText = Component.translatable("gui.rpg_mounts.bestiary.dismiss").getString();
                UIHelper.drawShadedButton(graphics, actBtnX, actBtnY, actBtnW, actBtnH, hoverBtn, 0xFF550000);
                graphics.drawString(this.font, labelText, actBtnX + (actBtnW - this.font.width(labelText)) / 2, actBtnY + 3, 0xFFFFFFFF, false);
            } else {
                String labelText = Component.translatable("gui.rpg_mounts.bestiary.summon").getString();
                UIHelper.drawShadedButton(graphics, actBtnX, actBtnY, actBtnW, actBtnH, hoverBtn, 0xFF005500);
                graphics.drawString(this.font, labelText, actBtnX + (actBtnW - this.font.width(labelText)) / 2, actBtnY + 3, 0xFFFFFFFF, false);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int panelW = Math.max(340, Math.min(440, (int)(this.width * 0.85)));
        int panelH = Math.max(200, Math.min(280, (int)(this.height * 0.85)));
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        int listX = left + 8;
        int listY = top + 8;
        int listW = (int)(panelW * 0.32);
        int listH = panelH - 16;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int rowH = 12;
            int visibleRows = (listH - 4) / rowH;
            int maxOffset = Math.max(0, sidebarItems.size() - visibleRows);
            if (amount > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else if (amount < 0) {
                scrollOffset = Math.min(maxOffset, scrollOffset + 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelW = Math.max(340, Math.min(440, (int)(this.width * 0.85)));
        int panelH = Math.max(200, Math.min(280, (int)(this.height * 0.85)));
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        int listX = left + 8;
        int listY = top + 8;
        int listW = (int)(panelW * 0.32);
        int listH = panelH - 16;

        int viewX = listX + listW + 8;
        int viewY = top + 8;
        int viewW = (int)(panelW * 0.38);
        int viewH = (int)(panelH * 0.45);
        int btnY = viewY + viewH - 14;
        int btnMinusX = viewX + viewW - 27;
        int btnPlusX = viewX + viewW - 14;

        if (selectedMount != null) {
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
        }

        // Left sidebar selection click
        int rowH = 12;
        int visibleRows = (listH - 4) / rowH;
        int itemY = listY + 3;
        int maxVisible = Math.min(scrollOffset + visibleRows, sidebarItems.size());

        if (button == 0) {
            for (int i = scrollOffset; i < maxVisible; i++) {
                SidebarItem item = sidebarItems.get(i);
                int drawY = itemY + (i - scrollOffset) * rowH;
                if (!item.isHeader) {
                    if (mouseX >= listX + 2 && mouseX <= listX + listW - (sidebarItems.size() > visibleRows ? 8 : 2) && mouseY >= drawY && mouseY <= drawY + rowH - 1) {
                        selectedMount = item.mount;
                        previewZoom = 1.0f;
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
                        return true;
                    }
                }
            }
        }

        // Action Button click
        if (selectedMount != null) {
            int statsX = viewX + viewW + 8;
            int statsW = panelW - (statsX - left) - 8;
            int statsY = top + 8;
            int statsH = panelH - 16;
            int actBtnW = statsW - 12;
            int actBtnH = 14;
            int actBtnX = statsX + 6;
            int actBtnY = statsY + statsH - actBtnH - 6;

            if (button == 0 && mouseX >= actBtnX && mouseX <= actBtnX + actBtnW && mouseY >= actBtnY && mouseY <= actBtnY + actBtnH) {
                RPGMountsClient.UnlockedMountInfo ownedInstance = getOwnedInstance(selectedMount.id);
                if (ownedInstance != null) {
                    boolean isActive = getActiveEntityOfTemplate(selectedMount.id) != null;
                    if (isActive) {
                        dev.architectury.networking.NetworkManager.sendToServer(ModPackets.C2S_DISMISS, new FriendlyByteBuf(Unpooled.buffer()));
                    } else {
                        FriendlyByteBuf summonBuf = new FriendlyByteBuf(Unpooled.buffer());
                        summonBuf.writeUtf(ownedInstance.instanceId);
                        NetworkManager.sendToServer(ModPackets.C2S_SUMMON, summonBuf);
                    }
                    if (Minecraft.getInstance().screen == this) {
                        Minecraft.getInstance().setScreen(null);
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
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

    private String truncate(String text, int length) {
        if (text.length() <= length) return text;
        return text.substring(0, length - 2) + "..";
    }
}
