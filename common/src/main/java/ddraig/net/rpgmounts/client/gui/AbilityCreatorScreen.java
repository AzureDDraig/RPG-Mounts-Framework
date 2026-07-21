package ddraig.net.rpgmounts.client.gui;

import ddraig.net.rpgmounts.client.integration.RPGWaypointsIntegration;
import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.network.ModPackets;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * RPG Mounts Ability Creator Screen
 * Premium gold-bordered dual-column UI for admins to visually design combat abilities.
 */
public class AbilityCreatorScreen extends Screen {
    private EditBox nameField;
    private EditBox soundField;
    private EditBox particleField;
    private EditBox animNameField;

    private String activeType = "DASH";
    private static final String[] TYPES = {"DASH", "PROJECTILE", "AOE", "BUFF", "STEALTH"};
    private int typeIdx = 0;

    private double damageVal = 5.0;
    private String damageType = "PHYSICAL";
    private static final String[] DAMAGE_TYPES = {"PHYSICAL", "FIRE", "MAGIC", "EXPLOSIVE"};
    private int dmgTypeIdx = 0;

    private int cooldownTicks = 100;
    private double staminaCost = 20.0;
    private double rangeVal = 5.0;
    private int particleCount = 10;
    private String vanillaAnim = "NONE";
    private static final String[] VANILLA_ANIMS = {"NONE", "WOLF_BITE", "HORSE_REAR", "DRAGON_BITE", "DRAGON_FLAP"};
    private int vanAnimIdx = 0;

    private int durationTicks = 0;
    private double powerVal = 5.0;

    public AbilityCreatorScreen() {
        super(Component.translatable("gui.rpg_mounts.ability_creator.title"));
    }

    @Override
    protected void init() {
        int panelW = 320;
        int panelH = 220;
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        int leftCol = left + 12;
        int rightCol = left + 168;

        this.nameField = new EditBox(this.font, leftCol, top + 26, 140, 12, Component.literal("Name"));
        this.soundField = new EditBox(this.font, rightCol, top + 26, 140, 12, Component.literal("Sound"));
        this.particleField = new EditBox(this.font, rightCol, top + 64, 140, 12, Component.literal("Particle"));
        this.animNameField = new EditBox(this.font, rightCol, top + 140, 140, 12, Component.literal("Custom Anim Name"));

        this.addWidget(this.nameField);
        this.addWidget(this.soundField);
        this.addWidget(this.particleField);
        this.addWidget(this.animNameField);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (Minecraft.getInstance().screen == this) {
            this.renderBackground(graphics);
        }

        int panelW = 320;
        int panelH = 220;
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        renderInTab(graphics, mouseX, mouseY, partialTicks, left, top, panelW, panelH);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelW = 320;
        int panelH = 220;
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        if (mouseClickedInTab(mouseX, mouseY, button, left, top, panelW, panelH)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void renderInTab(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, int x, int y, int width, int height) {
        int panelBorder = RPGWaypointsIntegration.getThemeColor("panelBorder", 0xFFDFD0A0); // Gold border
        int slotBg = RPGWaypointsIntegration.getThemeColor("slotBg", 0xFF1C1C1C); // Dark slot background
        int textColor = RPGWaypointsIntegration.getThemeColor("textColor", 0xFFCCCCCC);
        int textActiveColor = RPGWaypointsIntegration.getThemeColor("textActiveColor", 0xFFD4AF37); // Gold text

        // Title
        if (Minecraft.getInstance().screen == this) {
            graphics.drawString(this.font, this.title, x + 10, y - 14, textActiveColor, false);
        }

        // Background
        graphics.fill(x, y, x + width, y + height, slotBg);
        UIHelper.drawOutline(graphics, x, y, width, height, panelBorder);
        UIHelper.drawOutline(graphics, x + 2, y + 2, width - 4, height - 4, 0xFF000000);

        int leftColX = x + 12;
        int rightColX = x + 168;

        // Reposition fields dynamically relative to active tab offsets
        nameField.setX(leftColX);
        nameField.setY(y + 26);
        soundField.setX(rightColX);
        soundField.setY(y + 26);
        particleField.setX(rightColX);
        particleField.setY(y + 64);
        animNameField.setX(rightColX);
        animNameField.setY(y + 140);

        int adjustW = 100;
        int btnH = 14;

        // --- LEFT COLUMN ---
        int drawY = y + 14;
        graphics.drawString(this.font, "Ability Name:", leftColX, drawY, textColor, false);
        drawY += 12;
        nameField.render(graphics, mouseX, mouseY, partialTicks);

        drawY += 18;
        // Ability Type Selector
        graphics.drawString(this.font, "Type:", leftColX, drawY + 2, textColor, false);
        int typeBtnX = leftColX + 40;
        graphics.fill(typeBtnX, drawY, typeBtnX + adjustW, drawY + btnH, 0xFF2D2D2D);
        UIHelper.drawOutline(graphics, typeBtnX, drawY, adjustW, btnH, 0xFF777777);
        graphics.drawString(this.font, activeType, typeBtnX + (adjustW - this.font.width(activeType)) / 2, drawY + 3, 0xFFFFFFFF, false);

        drawY += 18;
        // Damage Adjuster
        drawAdjusterRow(graphics, "Dmg:", damageVal, "%.1f", leftColX, drawY, adjustW, btnH);

        drawY += 18;
        // Damage Type Selector
        graphics.drawString(this.font, "DmgType:", leftColX, drawY + 2, textColor, false);
        int dmgBtnX = leftColX + 40;
        graphics.fill(dmgBtnX, drawY, dmgBtnX + adjustW, drawY + btnH, 0xFF2D2D2D);
        UIHelper.drawOutline(graphics, dmgBtnX, drawY, adjustW, btnH, 0xFF777777);
        graphics.drawString(this.font, damageType, dmgBtnX + (adjustW - this.font.width(damageType)) / 2, drawY + 3, 0xFFFFFFFF, false);

        drawY += 18;
        // Cooldown Adjuster
        drawAdjusterRow(graphics, "CD:", cooldownTicks, "%d", leftColX, drawY, adjustW, btnH);

        drawY += 18;
        // Stamina Adjuster
        drawAdjusterRow(graphics, "Stm:", staminaCost, "%.1f", leftColX, drawY, adjustW, btnH);

        drawY += 18;
        // Range Adjuster
        drawAdjusterRow(graphics, "Rng:", rangeVal, "%.1f", leftColX, drawY, adjustW, btnH);

        // --- RIGHT COLUMN ---
        int rDrawY = y + 14;
        graphics.drawString(this.font, "Sound Resource:", rightColX, rDrawY, textColor, false);
        rDrawY += 12;
        soundField.render(graphics, mouseX, mouseY, partialTicks);

        rDrawY += 18;
        graphics.drawString(this.font, "Particle Resource:", rightColX, rDrawY, textColor, false);
        rDrawY += 12;
        particleField.render(graphics, mouseX, mouseY, partialTicks);

        rDrawY += 18;
        // Particle Count Adjuster
        drawAdjusterRow(graphics, "PrtCnt:", particleCount, "%d", rightColX, rDrawY, adjustW, btnH);

        rDrawY += 18;
        // Power Adjuster
        drawAdjusterRow(graphics, "Power:", powerVal, "%.1f", rightColX, rDrawY, adjustW, btnH);

        rDrawY += 18;
        // Duration Adjuster
        drawAdjusterRow(graphics, "Dur:", durationTicks, "%d", rightColX, rDrawY, adjustW, btnH);

        rDrawY += 18;
        // Vanilla Animation Selector
        graphics.drawString(this.font, "Anim:", rightColX, rDrawY + 2, textColor, false);
        int animBtnX = rightColX + 40;
        graphics.fill(animBtnX, rDrawY, animBtnX + adjustW, rDrawY + btnH, 0xFF2D2D2D);
        UIHelper.drawOutline(graphics, animBtnX, rDrawY, adjustW, btnH, 0xFF777777);
        graphics.drawString(this.font, vanillaAnim, animBtnX + (adjustW - this.font.width(vanillaAnim)) / 2, rDrawY + 3, 0xFFFFFFFF, false);

        rDrawY += 18;
        animNameField.render(graphics, mouseX, mouseY, partialTicks);

        // --- SAVE BUTTON ---
        int saveBtnW = 100;
        int saveBtnH = 18;
        int saveBtnX = x + (width - saveBtnW) / 2;
        int saveBtnY = y + height - 24;
        graphics.fill(saveBtnX, saveBtnY, saveBtnX + saveBtnW, saveBtnY + saveBtnH, 0xFF005500);
        UIHelper.drawOutline(graphics, saveBtnX, saveBtnY, saveBtnW, saveBtnH, 0xFF888888);
        String saveLabel = "Save Ability";
        graphics.drawString(this.font, saveLabel, saveBtnX + (saveBtnW - this.font.width(saveLabel)) / 2, saveBtnY + 5, 0xFFFFFFFF, false);
    }

    private void drawAdjusterRow(GuiGraphics graphics, String label, double val, String format, int x, int y, int width, int height) {
        int textColor = RPGWaypointsIntegration.getThemeColor("textColor", 0xFFCCCCCC);
        graphics.drawString(this.font, label, x, y + 2, textColor, false);
        
        int btnW = 12;
        int btnX = x + 40;
        
        // Minus
        graphics.fill(btnX, y, btnX + btnW, y + height, 0xFF2D2D2D);
        UIHelper.drawOutline(graphics, btnX, y, btnW, height, 0xFF777777);
        graphics.drawString(this.font, "-", btnX + 3, y + 3, 0xFFFFFFFF, false);

        // Value display
        String valStr;
        if (format.contains("d")) {
            valStr = String.format(format, (int) val);
        } else {
            valStr = String.format(format, val);
        }
        int displayW = width - 2 * btnW - 4;
        int displayX = btnX + btnW + 2;
        graphics.drawString(this.font, valStr, displayX + (displayW - this.font.width(valStr)) / 2, y + 3, 0xFFFFFFFF, false);

        // Plus
        int plusX = btnX + width - btnW;
        graphics.fill(plusX, y, plusX + btnW, y + height, 0xFF2D2D2D);
        UIHelper.drawOutline(graphics, plusX, y, btnW, height, 0xFF777777);
        graphics.drawString(this.font, "+", plusX + 3, y + 3, 0xFFFFFFFF, false);
    }

    public boolean mouseClickedInTab(double mouseX, double mouseY, int button, int x, int y, int width, int height) {
        int leftColX = x + 12;
        int rightColX = x + 168;
        int adjustW = 100;
        int btnH = 14;

        // Edit box focus clicks
        nameField.mouseClicked(mouseX, mouseY, button);
        soundField.mouseClicked(mouseX, mouseY, button);
        particleField.mouseClicked(mouseX, mouseY, button);
        animNameField.mouseClicked(mouseX, mouseY, button);

        // --- LEFT COLUMN CLICKS ---
        int drawY = y + 14 + 12 + 18; // Type Selector Y
        int typeBtnX = leftColX + 40;
        // Click Type
        if (mouseX >= typeBtnX && mouseX <= typeBtnX + adjustW && mouseY >= drawY && mouseY <= drawY + btnH) {
            typeIdx = (typeIdx + 1) % TYPES.length;
            activeType = TYPES[typeIdx];
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        drawY += 18; // Damage Y
        if (mouseY >= drawY && mouseY <= drawY + btnH) {
            int minusX = leftColX + 40;
            if (mouseX >= minusX && mouseX <= minusX + 12) {
                damageVal = Math.max(0.0, damageVal - 1.0);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            int plusX = minusX + adjustW - 12;
            if (mouseX >= plusX && mouseX <= plusX + 12) {
                damageVal += 1.0;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        drawY += 18; // Damage Type Y
        int dmgBtnX = leftColX + 40;
        if (mouseX >= dmgBtnX && mouseX <= dmgBtnX + adjustW && mouseY >= drawY && mouseY <= drawY + btnH) {
            dmgTypeIdx = (dmgTypeIdx + 1) % DAMAGE_TYPES.length;
            damageType = DAMAGE_TYPES[dmgTypeIdx];
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        drawY += 18; // Cooldown Y
        if (mouseY >= drawY && mouseY <= drawY + btnH) {
            int minusX = leftColX + 40;
            if (mouseX >= minusX && mouseX <= minusX + 12) {
                cooldownTicks = Math.max(20, cooldownTicks - 20);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            int plusX = minusX + adjustW - 12;
            if (mouseX >= plusX && mouseX <= plusX + 12) {
                cooldownTicks += 20;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        drawY += 18; // Stamina Y
        if (mouseY >= drawY && mouseY <= drawY + btnH) {
            int minusX = leftColX + 40;
            if (mouseX >= minusX && mouseX <= minusX + 12) {
                staminaCost = Math.max(0.0, staminaCost - 5.0);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            int plusX = minusX + adjustW - 12;
            if (mouseX >= plusX && mouseX <= plusX + 12) {
                staminaCost += 5.0;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        drawY += 18; // Range Y
        if (mouseY >= drawY && mouseY <= drawY + btnH) {
            int minusX = leftColX + 40;
            if (mouseX >= minusX && mouseX <= minusX + 12) {
                rangeVal = Math.max(1.0, rangeVal - 0.5);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            int plusX = minusX + adjustW - 12;
            if (mouseX >= plusX && mouseX <= plusX + 12) {
                rangeVal += 0.5;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        // --- RIGHT COLUMN CLICKS ---
        int rDrawY = y + 14 + 12 + 18 + 12 + 18; // Particle Count Y
        if (mouseY >= rDrawY && mouseY <= rDrawY + btnH) {
            int minusX = rightColX + 40;
            if (mouseX >= minusX && mouseX <= minusX + 12) {
                particleCount = Math.max(0, particleCount - 5);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            int plusX = minusX + adjustW - 12;
            if (mouseX >= plusX && mouseX <= plusX + 12) {
                particleCount += 5;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        rDrawY += 18; // Power Y
        if (mouseY >= rDrawY && mouseY <= rDrawY + btnH) {
            int minusX = rightColX + 40;
            if (mouseX >= minusX && mouseX <= minusX + 12) {
                powerVal = Math.max(0.0, powerVal - 0.5);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            int plusX = minusX + adjustW - 12;
            if (mouseX >= plusX && mouseX <= plusX + 12) {
                powerVal += 0.5;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        rDrawY += 18; // Duration Y
        if (mouseY >= rDrawY && mouseY <= rDrawY + btnH) {
            int minusX = rightColX + 40;
            if (mouseX >= minusX && mouseX <= minusX + 12) {
                durationTicks = Math.max(0, durationTicks - 20);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            int plusX = minusX + adjustW - 12;
            if (mouseX >= plusX && mouseX <= plusX + 12) {
                durationTicks += 20;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        rDrawY += 18; // Vanilla Anim Y
        int animBtnX = rightColX + 40;
        if (mouseX >= animBtnX && mouseX <= animBtnX + adjustW && mouseY >= rDrawY && mouseY <= rDrawY + btnH) {
            vanAnimIdx = (vanAnimIdx + 1) % VANILLA_ANIMS.length;
            vanillaAnim = VANILLA_ANIMS[vanAnimIdx];
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // --- SAVE CLICK ---
        int saveBtnW = 100;
        int saveBtnH = 18;
        int saveBtnX = x + (width - saveBtnW) / 2;
        int saveBtnY = y + height - 24;
        if (mouseX >= saveBtnX && mouseX <= saveBtnX + saveBtnW && mouseY >= saveBtnY && mouseY <= saveBtnY + saveBtnH) {
            String name = nameField.getValue();
            if (!name.isEmpty()) {
                MountData.AbilityData ab = new MountData.AbilityData();
                ab.name = name;
                ab.type = activeType;
                ab.damage = damageVal;
                ab.damageType = damageType;
                ab.cooldownTicks = cooldownTicks;
                ab.staminaCost = staminaCost;
                ab.range = rangeVal;
                ab.sound = soundField.getValue();
                ab.particle = particleField.getValue();
                ab.particleCount = particleCount;
                ab.power = powerVal;
                ab.durationTicks = durationTicks;
                ab.vanillaAnimation = vanillaAnim;
                ab.animationName = animNameField.getValue();

                // Send save request
                com.google.gson.Gson gson = new com.google.gson.Gson();
                String json = gson.toJson(ab);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeUtf(json);
                NetworkManager.sendToServer(ModPackets.C2S_CREATE_ABILITY, buf);

                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));

                if (Minecraft.getInstance().screen == this) {
                    this.onClose();
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameField.keyPressed(keyCode, scanCode, modifiers) ||
            this.soundField.keyPressed(keyCode, scanCode, modifiers) ||
            this.particleField.keyPressed(keyCode, scanCode, modifiers) ||
            this.animNameField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.nameField.charTyped(codePoint, modifiers) ||
            this.soundField.charTyped(codePoint, modifiers) ||
            this.particleField.charTyped(codePoint, modifiers) ||
            this.animNameField.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    public boolean isInitialized() {
        return this.minecraft != null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
