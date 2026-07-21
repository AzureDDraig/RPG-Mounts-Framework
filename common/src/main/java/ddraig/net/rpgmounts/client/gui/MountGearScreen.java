package ddraig.net.rpgmounts.client.gui;

import ddraig.net.rpgmounts.client.integration.RPGWaypointsIntegration;
import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.network.ModPackets;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * RPG Mounts Gear Screen
 * Renders the mount inventory panel, stats overview, equipment slots,
 * dynamic scrollable cargo container storage, and player inventory grid.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Renders base slots and stubs.
 * - 2026-06-19: [Inventory Redesign] - Added scrollable cargo grid, slot validations, tooltips, and shift-click integration.
 */
public class MountGearScreen extends Screen {
    private final RPGMountEntity mount;
    private final MountData template;

    private int scrollRowOffset = 0;
    private boolean isDraggingScrollbar = false;

    public MountGearScreen(RPGMountEntity mount) {
        super(Component.translatable("gui.rpg_mounts.gear.title"));
        this.mount = mount;
        this.template = MountRegistry.getTemplate(mount.getTemplateId());
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        int panelWidth = 176;
        int panelHeight = 220;
        
        ItemStack cargoStack = mount.getInventory().getItem(2);
        boolean hasCargo = !cargoStack.isEmpty();
        int cargoCapacity = 0;
        if (hasCargo && template != null) {
            String cargoId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cargoStack.getItem()).toString();
            cargoCapacity = template.allowed_cargo_map.getOrDefault(cargoId, 0);
        }

        int totalWidth = (hasCargo && cargoCapacity > 0) ? panelWidth + 184 : panelWidth;
        int left = (this.width - totalWidth) / 2;
        int top = (this.height - panelHeight) / 2;

        int borderC = RPGWaypointsIntegration.getThemeColor("panelBorder", 0xFFDFD0A0); // Gold
        int bgC = RPGWaypointsIntegration.getThemeColor("panelBg", 0xFF2D2D2D);
        int slotC = RPGWaypointsIntegration.getThemeColor("slotBg", 0xFF1C1C1C);
        int textActiveC = RPGWaypointsIntegration.getThemeColor("textActiveColor", 0xFFD4AF37); // Gold text
        int textNormalC = RPGWaypointsIntegration.getThemeColor("textColor", 0xFFCCCCCC);

        // Draw double borders
        graphics.fill(left, top, left + totalWidth, top + panelHeight, bgC);
        UIHelper.drawOutline(graphics, left, top, totalWidth, panelHeight, borderC);
        UIHelper.drawOutline(graphics, left + 2, top + 2, totalWidth - 4, panelHeight - 4, 0xFF000000);

        // Header Title
        graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.gear.title").getString(), left + 8, top + 6, textActiveC, false);

        // Left Panel (Stats Overview)
        int statsY = top + 20;
        graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.gear.stats").getString(), left + 8, statsY, textActiveC, false);
        graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.hud.stat.hp").getString() + ": " + (int)mount.getHealth() + "/" + (int)mount.getMaxHealth(), left + 8, statsY + 12, textNormalC, false);
        graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.hud.stat.stm").getString() + ": " + (int)mount.getStamina(), left + 8, statsY + 24, textNormalC, false);
        graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.hud.stat.bond").getString() + ": " + mount.getBonding() + "/100", left + 8, statsY + 36, textNormalC, false);
        graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.hud.stat.speed").getString() + ": " + String.format("%.2f", mount.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)), left + 8, statsY + 48, textNormalC, false);

        // Center Panel (Equipment Slots)
        int equipX = left + 106;
        int equipY = top + 20;
        graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.gear.equipped").getString(), equipX, equipY, textActiveC, false);
        equipY += 12;

        // Saddle slot (0)
        drawSlot(graphics, equipX, equipY, mount.getInventory().getItem(0), Component.translatable("gui.rpg_mounts.gear.slot.saddle").getString(), mouseX, mouseY);
        // Armor slot (1)
        drawSlot(graphics, equipX, equipY + 22, mount.getInventory().getItem(1), Component.translatable("gui.rpg_mounts.gear.slot.armor").getString(), mouseX, mouseY);
        // Cargo slot (2)
        drawSlot(graphics, equipX, equipY + 44, cargoStack, Component.translatable("gui.rpg_mounts.gear.slot.cargo").getString(), mouseX, mouseY);

        // Enhancers (3-6)
        int enhancerX = equipX + 22;
        int enhancerY = top + 32;
        for (int i = 0; i < 4; i++) {
            drawSlot(graphics, enhancerX, enhancerY + (i * 22), mount.getInventory().getItem(3 + i), Component.translatable("gui.rpg_mounts.gear.slot.enhancer", (i + 1)).getString(), mouseX, mouseY);
        }

        // Player Inventory Title
        graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.gear.inventory").getString(), left + 8, top + 118, textActiveC, false);

        // Player slots (hotbar + inventory)
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            // Main Inventory (27 slots, row 0-2, col 0-8)
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    int slotX = left + 7 + c * 18;
                    int slotY = top + 128 + r * 18;
                    int idx = 9 + r * 9 + c;
                    drawSlotBox(graphics, slotX, slotY, player.getInventory().getItem(idx), mouseX, mouseY);
                }
            }
            // Hotbar (9 slots, col 0-8)
            for (int c = 0; c < 9; c++) {
                int slotX = left + 7 + c * 18;
                int slotY = top + 186;
                drawSlotBox(graphics, slotX, slotY, player.getInventory().getItem(c), mouseX, mouseY);
            }
        }

        // Sliding Cargo Container slots
        if (hasCargo && cargoCapacity > 0) {
            int cargoPanelX = left + 176;
            graphics.fill(cargoPanelX, top + 4, cargoPanelX + 1, top + panelHeight - 4, 0xFF000000); // Seperator
            
            int cargoTitleX = cargoPanelX + 8;
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.gear.cargo_storage").getString(), cargoTitleX, top + 6, textActiveC, false);

            int totalRows = (int) Math.ceil(cargoCapacity / 9.0);
            int maxRowOffset = Math.max(0, totalRows - 5);
            scrollRowOffset = Math.min(scrollRowOffset, maxRowOffset);

            // Render visible cargo slots (5 rows of 9)
            int cargoSlotsStartY = top + 20;
            for (int r = 0; r < 5; r++) {
                int rowIdx = r + scrollRowOffset;
                for (int c = 0; c < 9; c++) {
                    int slotX = cargoPanelX + 8 + c * 18;
                    int slotY = cargoSlotsStartY + r * 18;
                    int flatIdx = rowIdx * 9 + c;

                    if (flatIdx < cargoCapacity) {
                        drawSlotBox(graphics, slotX, slotY, mount.getInventory().getItem(7 + flatIdx), mouseX, mouseY);
                    } else {
                        // Dead slots out of capacity bounds
                        graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0x40101010);
                        UIHelper.drawOutline(graphics, slotX, slotY, 18, 18, 0xFF333333);
                    }
                }
            }

            // Draw scrollbar if capacity exceeds 45 slots
            if (totalRows > 5) {
                int scrollbarX = cargoPanelX + 8 + 9 * 18 + 2;
                int scrollbarY = cargoSlotsStartY;
                int scrollbarH = 90;
                graphics.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + scrollbarH, 0xFF101010);
                UIHelper.drawOutline(graphics, scrollbarX, scrollbarY, 6, scrollbarH, 0xFF444444);

                int thumbH = Math.max(15, (5 * scrollbarH) / totalRows);
                int thumbY = scrollbarY + ((scrollbarH - thumbH) * scrollRowOffset) / maxRowOffset;

                graphics.fill(scrollbarX + 1, thumbY, scrollbarX + 5, thumbY + thumbH, 0xFFD4AF37); // Gold thumb
                UIHelper.drawOutline(graphics, scrollbarX, thumbY, 6, thumbH, 0xFF888888);
            }
        }

        // Render hover tooltips (done after drawing slots to prevent item overlaps)
        renderHoverTooltips(graphics, mouseX, mouseY, left, top, panelWidth, panelHeight, hasCargo, cargoCapacity);

        // Render carried item stack following cursor
        if (player != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0, 0.0, 250.0);
                graphics.renderItem(carried, mouseX - 8, mouseY - 8);
                graphics.renderItemDecorations(this.font, carried, mouseX - 8, mouseY - 8);
                graphics.pose().popPose();
            }
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y, ItemStack stack, String placeholderText, int mouseX, int mouseY) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF181818);
        UIHelper.drawOutline(graphics, x, y, 18, 18, 0xFF666666);

        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(this.font, stack, x + 1, y + 1);
        } else {
            // Draw brief placeholder label
            graphics.drawString(this.font, placeholderText.substring(0, 1), x + 6, y + 5, 0x88888888, false);
        }

        if (mouseX >= x && mouseX <= x + 18 && mouseY >= y && mouseY <= y + 18) {
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x30FFFFFF);
        }
    }

    private void drawSlotBox(GuiGraphics graphics, int x, int y, ItemStack stack, int mouseX, int mouseY) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF222222);
        UIHelper.drawOutline(graphics, x, y, 18, 18, 0xFF555555);

        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(this.font, stack, x + 1, y + 1);
        }

        if (mouseX >= x && mouseX <= x + 18 && mouseY >= y && mouseY <= y + 18) {
            graphics.fill(x, y, x + 18, y + 18, 0x30FFFFFF);
        }
    }

    private void renderHoverTooltips(GuiGraphics graphics, int mouseX, int mouseY, int left, int top, int panelW, int panelH, boolean hasCargo, int cargoCapacity) {
        // 1. Center Equipment slots
        int equipX = left + 106;
        int equipY = top + 20 + 12;

        if (checkHover(mouseX, mouseY, equipX, equipY, 18, 18)) {
            renderSlotTooltip(graphics, mount.getInventory().getItem(0), Component.translatable("gui.rpg_mounts.gear.slot.saddle_tooltip"), Component.translatable("gui.rpg_mounts.gear.slot.saddle.desc"), mouseX, mouseY);
            return;
        }
        if (checkHover(mouseX, mouseY, equipX, equipY + 22, 18, 18)) {
            renderSlotTooltip(graphics, mount.getInventory().getItem(1), Component.translatable("gui.rpg_mounts.gear.slot.armor_tooltip"), Component.translatable("gui.rpg_mounts.gear.slot.armor.desc"), mouseX, mouseY);
            return;
        }
        if (checkHover(mouseX, mouseY, equipX, equipY + 44, 18, 18)) {
            if (mount.getInventory().getItem(2).isEmpty() && template != null) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable("gui.rpg_mounts.gear.slot.cargo.empty").withStyle(ChatFormatting.GOLD));
                tooltip.add(Component.translatable("gui.rpg_mounts.gear.slot.cargo.empty.desc"));
                tooltip.add(Component.translatable("gui.rpg_mounts.gear.slot.cargo.compatible").withStyle(ChatFormatting.YELLOW));
                for (Map.Entry<String, Integer> entry : template.allowed_cargo_map.entrySet()) {
                    String cleanName = entry.getKey().substring(entry.getKey().indexOf(":") + 1).replace("_", " ");
                    tooltip.add(Component.literal(" - " + cleanName + " (" + entry.getValue() + " slots)").withStyle(ChatFormatting.GRAY));
                }
                graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
            } else {
                renderSlotTooltip(graphics, mount.getInventory().getItem(2), Component.translatable("gui.rpg_mounts.gear.slot.cargo_tooltip"), Component.translatable("gui.rpg_mounts.gear.slot.cargo.desc"), mouseX, mouseY);
            }
            return;
        }

        // 2. Enhancer Slots
        int enhancerX = equipX + 22;
        int enhancerY = top + 32;
        for (int i = 0; i < 4; i++) {
            if (checkHover(mouseX, mouseY, enhancerX, enhancerY + (i * 22), 18, 18)) {
                renderSlotTooltip(graphics, mount.getInventory().getItem(3 + i), Component.translatable("gui.rpg_mounts.gear.slot.enhancer_tooltip", (i + 1)), Component.translatable("gui.rpg_mounts.gear.slot.enhancer.desc"), mouseX, mouseY);
                return;
            }
        }

        // 3. Player slots
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    int slotX = left + 7 + c * 18;
                    int slotY = top + 128 + r * 18;
                    if (checkHover(mouseX, mouseY, slotX, slotY, 18, 18)) {
                        ItemStack item = player.getInventory().getItem(9 + r * 9 + c);
                        if (!item.isEmpty()) {
                            graphics.renderTooltip(this.font, item, mouseX, mouseY);
                        }
                        return;
                    }
                }
            }
            for (int c = 0; c < 9; c++) {
                int slotX = left + 7 + c * 18;
                int slotY = top + 186;
                if (checkHover(mouseX, mouseY, slotX, slotY, 18, 18)) {
                    ItemStack item = player.getInventory().getItem(c);
                    if (!item.isEmpty()) {
                        graphics.renderTooltip(this.font, item, mouseX, mouseY);
                    }
                    return;
                }
            }
        }

        // 4. Cargo storage slots
        if (hasCargo && cargoCapacity > 0) {
            int cargoPanelX = left + 176;
            int cargoSlotsStartY = top + 20;
            int totalRows = (int) Math.ceil(cargoCapacity / 9.0);
            for (int r = 0; r < 5; r++) {
                int rowIdx = r + scrollRowOffset;
                for (int c = 0; c < 9; c++) {
                    int slotX = cargoPanelX + 8 + c * 18;
                    int slotY = cargoSlotsStartY + r * 18;
                    int flatIdx = rowIdx * 9 + c;
                    if (flatIdx < cargoCapacity && checkHover(mouseX, mouseY, slotX, slotY, 18, 18)) {
                        ItemStack item = mount.getInventory().getItem(7 + flatIdx);
                        if (!item.isEmpty()) {
                            graphics.renderTooltip(this.font, item, mouseX, mouseY);
                        }
                        return;
                    }
                }
            }
        }
    }

    private void renderSlotTooltip(GuiGraphics graphics, ItemStack stack, Component slotName, Component description, int mouseX, int mouseY) {
        if (!stack.isEmpty()) {
            graphics.renderTooltip(this.font, stack, mouseX, mouseY);
        } else {
            List<Component> list = new ArrayList<>();
            list.add(slotName.copy().withStyle(ChatFormatting.GOLD));
            list.add(description.copy().withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(this.font, list, mouseX, mouseY);
        }
    }

    private boolean checkHover(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelWidth = 176;
        int panelHeight = 220;

        ItemStack cargoStack = mount.getInventory().getItem(2);
        boolean hasCargo = !cargoStack.isEmpty();
        int cargoCapacity = 0;
        if (hasCargo && template != null) {
            String cargoId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cargoStack.getItem()).toString();
            cargoCapacity = template.allowed_cargo_map.getOrDefault(cargoId, 0);
        }

        int totalWidth = (hasCargo && cargoCapacity > 0) ? panelWidth + 184 : panelWidth;
        int left = (this.width - totalWidth) / 2;
        int top = (this.height - panelHeight) / 2;

        // 1. Check clicks on Center Equipment slots
        int equipX = left + 106;
        int equipY = top + 20 + 12;

        if (checkHover((int)mouseX, (int)mouseY, equipX, equipY, 18, 18)) {
            dispatchClick(0, button);
            return true;
        }
        if (checkHover((int)mouseX, (int)mouseY, equipX, equipY + 22, 18, 18)) {
            dispatchClick(1, button);
            return true;
        }
        if (checkHover((int)mouseX, (int)mouseY, equipX, equipY + 44, 18, 18)) {
            dispatchClick(2, button);
            return true;
        }

        // 2. Enhancer slots (3-6)
        int enhancerX = equipX + 22;
        int enhancerY = top + 32;
        for (int i = 0; i < 4; i++) {
            if (checkHover((int)mouseX, (int)mouseY, enhancerX, enhancerY + (i * 22), 18, 18)) {
                dispatchClick(3 + i, button);
                return true;
            }
        }

        // 3. Player slots
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            // Inventory slots
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    int slotX = left + 7 + c * 18;
                    int slotY = top + 128 + r * 18;
                    if (checkHover((int)mouseX, (int)mouseY, slotX, slotY, 18, 18)) {
                        dispatchClick(1000 + 9 + r * 9 + c, button);
                        return true;
                    }
                }
            }
            // Hotbar slots
            for (int c = 0; c < 9; c++) {
                int slotX = left + 7 + c * 18;
                int slotY = top + 186;
                if (checkHover((int)mouseX, (int)mouseY, slotX, slotY, 18, 18)) {
                    dispatchClick(1000 + c, button);
                    return true;
                }
            }
        }

        // 4. Cargo slots
        if (hasCargo && cargoCapacity > 0) {
            int cargoPanelX = left + 176;
            int cargoSlotsStartY = top + 20;
            int totalRows = (int) Math.ceil(cargoCapacity / 9.0);

            for (int r = 0; r < 5; r++) {
                int rowIdx = r + scrollRowOffset;
                for (int c = 0; c < 9; c++) {
                    int slotX = cargoPanelX + 8 + c * 18;
                    int slotY = cargoSlotsStartY + r * 18;
                    int flatIdx = rowIdx * 9 + c;
                    if (flatIdx < cargoCapacity && checkHover((int)mouseX, (int)mouseY, slotX, slotY, 18, 18)) {
                        dispatchClick(7 + flatIdx, button);
                        return true;
                    }
                }
            }

            // Scrollbar drag start
            if (totalRows > 5) {
                int scrollbarX = cargoPanelX + 8 + 9 * 18 + 2;
                int scrollbarY = cargoSlotsStartY;
                if (mouseX >= scrollbarX && mouseX <= scrollbarX + 6 && mouseY >= scrollbarY && mouseY <= scrollbarY + 90) {
                    isDraggingScrollbar = true;
                    updateScrollFromMouse(mouseY, scrollbarY, totalRows);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollbar && template != null) {
            ItemStack cargoStack = mount.getInventory().getItem(2);
            int cargoCapacity = 0;
            if (!cargoStack.isEmpty()) {
                String cargoId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cargoStack.getItem()).toString();
                cargoCapacity = template.allowed_cargo_map.getOrDefault(cargoId, 0);
            }
            int totalRows = (int) Math.ceil(cargoCapacity / 9.0);
            int cargoPanelX = (this.width - (176 + 184)) / 2 + 176;
            int scrollbarY = (this.height - 220) / 2 + 20;
            updateScrollFromMouse(mouseY, scrollbarY, totalRows);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (template != null) {
            ItemStack cargoStack = mount.getInventory().getItem(2);
            if (!cargoStack.isEmpty()) {
                String cargoId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cargoStack.getItem()).toString();
                int cargoCapacity = template.allowed_cargo_map.getOrDefault(cargoId, 0);
                int totalRows = (int) Math.ceil(cargoCapacity / 9.0);
                int maxRowOffset = Math.max(0, totalRows - 5);
                
                if (amount > 0) {
                    scrollRowOffset = Math.max(0, scrollRowOffset - 1);
                } else if (amount < 0) {
                    scrollRowOffset = Math.min(maxRowOffset, scrollRowOffset + 1);
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private void updateScrollFromMouse(double mouseY, int scrollbarY, int totalRows) {
        int scrollbarH = 90;
        int maxRowOffset = Math.max(0, totalRows - 5);
        if (maxRowOffset > 0) {
            double ratio = (mouseY - scrollbarY) / (double) scrollbarH;
            ratio = Math.max(0.0, Math.min(1.0, ratio));
            scrollRowOffset = (int) Math.round(ratio * maxRowOffset);
        }
    }

    private void dispatchClick(int slotIdx, int button) {
        // Send interaction to server
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(mount.getId());
        buf.writeInt(slotIdx);
        // Determine interaction code: left = 0, right = 1, shift = 2
        int clickType = Screen.hasShiftDown() ? 2 : (button == 1 ? 1 : 0);
        buf.writeInt(clickType);
        NetworkManager.sendToServer(ModPackets.C2S_GEAR_CLICK, buf);
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
