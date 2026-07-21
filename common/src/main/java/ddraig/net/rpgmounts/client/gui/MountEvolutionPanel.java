package ddraig.net.rpgmounts.client.gui;

import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * RPG Mounts Evolution Sidebar Panel
 * Displays evolution node paths, milestone checklists, required catalyst items, and transformation button.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented MountEvolutionPanel screen with requirements check list and evolution transition actions.
 * - 2026-06-19: [Fix Compile Errors] - Use connection.sendCommand instead of raw ServerboundChatCommandPacket/sendChatCommand.
 */
public class MountEvolutionPanel extends Screen {
    private final RPGMountEntity mount;
    private final MountData template;

    public MountEvolutionPanel(RPGMountEntity mount) {
        super(Component.translatable("gui.rpg_mounts.evolution.panel.title"));
        this.mount = mount;
        this.template = MountRegistry.getTemplate(mount.getTemplateId());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        int panelWidth = 120;
        int panelHeight = 175;
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;

        // Draw background box
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xD0101010);
        UIHelper.drawOutline(graphics, left, top, panelWidth, panelHeight, 0xFF6D4D2E); // Wood accent

        // Header Title
        graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.evolution.panel.paths").getString(), left + 8, top + 8, 0xFFDFD0A0, false);

        if (template != null && template.evolution != null && !template.evolution.targetId.isEmpty()) {
            MountData target = MountRegistry.getTemplate(template.evolution.targetId);
            String targetName = (target != null) ? target.name : template.evolution.targetId;

            // Target Mount Card Info
            int textY = top + 26;
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.evolution.panel.next", targetName).getString(), left + 8, textY, 0xFFFFFFFF, false);

            // Requirements checklist
            textY += 16;
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.evolution.panel.requirements").getString(), left + 8, textY, 0xFFFFFF00, false);

            // Bonding score requirement check
            textY += 12;
            int currentBonding = mount.getBonding();
            int requiredBonding = template.evolution.requiredBonding;
            int bondingColor = (currentBonding >= requiredBonding) ? 0xFF00FF00 : 0xFFFF0000;
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.evolution.panel.bonding", currentBonding, requiredBonding).getString(), left + 8, textY, bondingColor, false);

            // Required items check
            textY += 12;
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.evolution.panel.catalyst").getString(), left + 8, textY, 0xFFCCCCCC, false);
            textY += 12;
            
            // Draw dummy required item Catalyst (Nether Star)
            int itemX = left + 12;
            int itemY = textY;
            graphics.fill(itemX, itemY, itemX + 18, itemY + 18, 0xFF252525);
            UIHelper.drawOutline(graphics, itemX, itemY, 18, 18, 0xFF555555);
            graphics.renderItem(new ItemStack(Items.NETHER_STAR), itemX + 1, itemY + 1);
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.evolution.panel.star_count").getString(), itemX + 22, itemY + 5, 0xFFCCCCCC, false);

            // Action Evolve Button
            int btnX = left + 10;
            int btnY = top + 145;
            boolean canEvolve = (currentBonding >= requiredBonding);
            int btnBg = canEvolve ? 0xFF00AA00 : 0xFF555555;
            graphics.fill(btnX, btnY, btnX + 100, btnY + 20, btnBg);
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.hud.evolve").getString(), btnX + 32, btnY + 6, 0xFFFFFFFF, false);
        } else {
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.evolution.panel.no_path").getString(), left + 12, top + 50, 0xFF888888, false);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (this.width - 120) / 2;
        int top = (this.height - 175) / 2;

        int btnX = left + 10;
        int btnY = top + 145;
        if (mouseX >= btnX && mouseX <= btnX + 100 && mouseY >= btnY && mouseY <= btnY + 20) {
            if (template != null && template.evolution != null && !template.evolution.targetId.isEmpty()) {
                if (mount.getBonding() >= template.evolution.requiredBonding && this.minecraft != null && this.minecraft.player != null) {
                    // Send evolution command / packet
                    this.minecraft.player.connection.sendCommand("rpg_mounts evolve " + template.evolution.targetId);
                    this.onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
