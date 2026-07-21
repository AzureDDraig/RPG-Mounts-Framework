package ddraig.net.rpgmounts.client.gui;

import ddraig.net.rpgmounts.config.ModConfig;
import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * RPG Mounts Stamina HUD Overlay class
 * Draws custom HUD elements representing the mount's stamina using 24x24 pixel art icons.
 */
public class MountStaminaOverlay {
    private static final ResourceLocation STAMINA_TEX = new ResourceLocation("rpg_mounts", "textures/gui/stamina_bars.png");

    public static void render(GuiGraphics graphics, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof RPGMountEntity mount) {
            if (!ModConfig.get().stamina.enable_stamina_system) return;

            MountData data = MountRegistry.getTemplate(mount.getTemplateId());
            if (data == null) return;

            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            float stamina = mount.getStamina();
            float maxStamina = (float) data.stats.maxStamina;
            float percent = Math.max(0.0f, Math.min(1.0f, stamina / maxStamina));

            int totalSegments = 10;
            int spacing = 10; // Overlap icons slightly (12x12 size with 10px step)
            int barWidth = (spacing * (totalSegments - 1)) + 12;
            int startX = (screenWidth / 2) - barWidth / 2;
            int y = screenHeight - 68; // Positioned perfectly to avoid overlapping vanilla HUD elements

            int staminaIconType = data.staminaIconType; // 0 to 4
            int texY = 0;
            if (data.category.equalsIgnoreCase("AQUATIC")) {
                texY = 24;
            } else if (data.category.equalsIgnoreCase("FLYING")) {
                texY = 48;
            }

            int texXEmpty = staminaIconType * 48;
            int texXFilled = staminaIconType * 48 + 24;

            for (int i = 0; i < totalSegments; i++) {
                float threshold = (i + 1) / (float) totalSegments;
                boolean filled = percent >= threshold;
                int sx = startX + i * spacing;
                int u = filled ? texXFilled : texXEmpty;

                graphics.blit(STAMINA_TEX, sx, y, 12, 12, (float) u, (float) texY, 24, 24, 256, 128);
            }
        }
    }
}
