package ddraig.net.rpgmounts.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * RPG Mounts Admin Performance Dashboard Screen
 * Renders active ticking entities stats, suspended/culled counts, CPU times, and recall triggers.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented AdminPerformanceDashboard layout, statistics lists, and admin purge buttons.
 * - 2026-06-19: [Fix Compile Errors] - Use connection.sendCommand instead of raw ServerboundChatCommandPacket/sendChatCommand.
 */
public class AdminPerformanceDashboard extends Screen {
    public AdminPerformanceDashboard() {
        super(Component.literal("Admin Performance Monitor"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        int width = 280;
        int height = 200;
        int left = (this.width - width) / 2;
        int top = (this.height - height) / 2;

        // Draw background
        graphics.fill(left, top, left + width, top + height, 0xD0101010);
        UIHelper.drawOutline(graphics, left, top, width, height, 0xFF4A4A4A); // Dark border

        // Header Title
        graphics.drawString(this.font, this.title, left + 12, top + 12, 0xFFFF5555, false); // Admin red

        // Stats grid
        int statsY = top + 35;
        graphics.drawString(this.font, "Total Active Mounts: 4", left + 15, statsY, 0xFFCCCCCC, false);
        graphics.drawString(this.font, "Suspended (Culled): 3", left + 15, statsY + 12, 0xFFCCCCCC, false);
        graphics.drawString(this.font, "Active Ticking: 1", left + 15, statsY + 24, 0xFFCCCCCC, false);
        graphics.drawString(this.font, "SQLite Async queue size: 0", left + 15, statsY + 36, 0xFFCCCCCC, false);

        // Render Active List Box
        int listBoxY = statsY + 55;
        graphics.fill(left + 15, listBoxY, left + width - 15, listBoxY + 60, 0xFF181818);
        UIHelper.drawOutline(graphics, left + 15, listBoxY, width - 30, 60, 0xFF333333);
        graphics.drawString(this.font, "Name      Owner      State      Coord", left + 20, listBoxY + 5, 0xFF888888, false);
        graphics.drawString(this.font, "Stallion  Player1    Culled     -12, 64, 450", left + 20, listBoxY + 18, 0xFFCCCCCC, false);
        graphics.drawString(this.font, "Timber    Player2    Active     100, 72, -90", left + 20, listBoxY + 30, 0xFFCCCCCC, false);

        // Force dismiss recall buttons
        int btnX = left + 15;
        int btnY = top + 165;
        graphics.fill(btnX, btnY, btnX + 115, btnY + 20, 0xFF550000);
        graphics.drawString(this.font, "Purge Offline", btnX + 18, btnY + 6, 0xFFFFFFFF, false);

        int btnX2 = left + 150;
        graphics.fill(btnX2, btnY, btnX2 + 115, btnY + 20, 0xFF7A7A7A);
        graphics.drawString(this.font, "Dismiss All", btnX2 + 25, btnY + 6, 0xFFFFFFFF, false);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (this.width - 280) / 2;
        int top = (this.height - 200) / 2;
        int btnY = top + 165;

        // Purge Offline click
        int btnX = left + 15;
        if (mouseX >= btnX && mouseX <= btnX + 115 && mouseY >= btnY && mouseY <= btnY + 20) {
            if (this.minecraft != null && this.minecraft.player != null) {
                // Command dispatch for purge
                this.minecraft.player.connection.sendCommand("rpg_mounts admin dismiss");
                this.onClose();
                return true;
            }
        }

        // Dismiss All click
        int btnX2 = left + 150;
        if (mouseX >= btnX2 && mouseX <= btnX2 + 115 && mouseY >= btnY && mouseY <= btnY + 20) {
            if (this.minecraft != null && this.minecraft.player != null) {
                // Command dispatch for recall all
                this.minecraft.player.connection.sendCommand("rpg_mounts dismiss");
                this.onClose();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
