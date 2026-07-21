package ddraig.net.rpgmounts.evolution.client;

import com.mojang.blaze3d.systems.RenderSystem;
import ddraig.net.rpgmounts.RPGMounts;
import ddraig.net.rpgmounts.api.IEvolutionProvider.EvolutionPathInfo;
import ddraig.net.rpgmounts.api.EvolutionAPI;
import ddraig.net.rpgmounts.client.gui.UIHelper;
import ddraig.net.rpgmounts.data.DatabaseManager;
import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.evolution.config.EvolutionTreeManager;
import ddraig.net.rpgmounts.evolution.network.EvolutionPackets;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;

/**
 * Visual branching tree browser screen representing the player's mount evolution pathways.
 * Supports panning, cursor-anchored zoom metrics, spline routing, and detail checklists.
 */
public class AddonEvolutionTreeScreen extends Screen {
    private final RPGMountEntity mount;
    private final String baseTemplateId;
    
    // Viewport Transformation parameters
    private double panX = 0;
    private double panY = 0;
    private double zoom = 1.0;
    
    // Inertial panning physics variables
    private double velX = 0;
    private double velY = 0;
    private long lastTime = 0;

    // Easing transition variables
    private boolean isEasing = false;
    private double startPanX, startPanY, startZoom;
    private double targetPanX, targetPanY, targetZoom;
    private long easingStartTime = 0;
    private static final long EASING_DURATION = 250; // ms

    // Rendering assets and dimensions
    private static final int CARD_W = 32;
    private static final int CARD_H = 32;
    private static final int DRAWER_W = 120;

    private EvolutionTreeManager.EvolutionBranch selectedBranch = null;
    private EvolutionPathInfo selectedPathInfo = null;

    public AddonEvolutionTreeScreen(RPGMountEntity mount) {
        super(Component.translatable("rpg_mounts.evolution.pathways.title"));
        this.mount = mount;
        this.baseTemplateId = mount.getTemplateId();
    }

    @Override
    protected void init() {
        super.init();
        this.lastTime = System.currentTimeMillis();
        this.panX = 0;
        this.panY = 0;
        this.zoom = 1.0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xDD0a0b0e);

        updateViewportPhysics();
        drawGridOverlay(graphics);

        double canvasMouseX = (mouseX - width / 2.0 - panX) / zoom;
        double canvasMouseY = (mouseY - height / 2.0 - panY) / zoom;

        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0 + panX, height / 2.0 + panY, 0.0);
        graphics.pose().scale((float) zoom, (float) zoom, 1.0f);

        renderSplineConnections(graphics);
        renderBranchNodes(graphics, canvasMouseX, canvasMouseY);

        graphics.pose().popPose();

        renderHUDDrawer(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void updateViewportPhysics() {
        long now = System.currentTimeMillis();
        double dt = (now - lastTime) / 1000.0;
        lastTime = now;

        if (isEasing) {
            long elapsed = now - easingStartTime;
            if (elapsed >= EASING_DURATION) {
                panX = targetPanX;
                panY = targetPanY;
                zoom = targetZoom;
                isEasing = false;
            } else {
                double alpha = (double) elapsed / EASING_DURATION;
                double phi = 3 * alpha * alpha - 2 * alpha * alpha * alpha; // cubic ease-in-ease-out
                panX = startPanX + phi * (targetPanX - startPanX);
                panY = startPanY + phi * (targetPanY - startPanY);
                zoom = startZoom + phi * (targetZoom - startZoom);
            }
        } else {
            if (Math.abs(velX) > 0.05 || Math.abs(velY) > 0.05) {
                panX += velX * dt * 1000.0;
                panY += velY * dt * 1000.0;
                double friction = Math.pow(0.92, dt * 60.0);
                velX *= friction;
                velY *= friction;
            } else {
                velX = 0;
                velY = 0;
            }
        }
    }

    private void drawGridOverlay(GuiGraphics graphics) {
        int dotSpacing = 16;
        int gridColor = UIHelper.blendColors(0x1F000000, 0x1Fffffff, (float) Math.max(0.0, Math.min(1.0, (zoom - 0.5) / 1.5)));
        
        double startX = (( - panX - width / 2.0) % dotSpacing);
        double startY = (( - panY - height / 2.0) % dotSpacing);

        RenderSystem.enableBlend();
        for (double x = startX; x < width; x += dotSpacing * zoom) {
            for (double y = startY; y < height; y += dotSpacing * zoom) {
                graphics.fill((int) x, (int) y, (int) (x + 1.5), (int) (y + 1.5), gridColor);
            }
        }
    }

    private void renderSplineConnections(GuiGraphics graphics) {
        EvolutionTreeManager.EvolutionTree tree = EvolutionTreeManager.loadedTrees.get(baseTemplateId);
        if (tree == null) return;

        for (EvolutionTreeManager.EvolutionBranch branch : tree.branches) {
            int sx = 0, sy = 0;
            if (branch.parentId != null && !branch.parentId.isEmpty()) {
                for (EvolutionTreeManager.EvolutionBranch other : tree.branches) {
                    if (other.targetId.equals(branch.parentId)) {
                        sx = other.xCoord;
                        sy = other.yCoord;
                        break;
                    }
                }
            }
            int color = branch.excludes.isEmpty() ? 0xFFcda84b : 0xFFa93b3b;
            
            int prevX = sx;
            int prevY = sy;
            for (EvolutionTreeManager.Vec2i pt : branch.reroutePoints) {
                drawBezierSpline(graphics, prevX, prevY, pt.x, pt.y, color);
                prevX = pt.x;
                prevY = pt.y;
            }
            drawBezierSpline(graphics, prevX, prevY, branch.xCoord, branch.yCoord, color);
        }
    }

    private void drawBezierSpline(GuiGraphics graphics, int sx, int sy, int ex, int ey, int color) {
        double dx = Math.abs(ex - sx);
        double w = Math.max(32, Math.min(128, dx / 2.0));
        
        double p0x = sx;
        double p0y = sy;
        double p1x = sx + w;
        double p1y = sy;
        double p2x = ex - w;
        double p2y = ey;
        double p3x = ex;
        double p3y = ey;

        int segments = (int) Math.max(12, Math.min(64, 32 * zoom));
        double prevX = p0x;
        double prevY = p0y;

        List<Vec2d> pts = new ArrayList<>();
        pts.add(new Vec2d(p0x, p0y));
        double totalLen = 0;
        double[] lut = new double[segments + 1];
        lut[0] = 0;

        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            double t1 = 1.0 - t;
            double x = t1 * t1 * t1 * p0x + 3 * t1 * t1 * t * p1x + 3 * t1 * t * t * p2x + t * t * t * p3x;
            double y = t1 * t1 * t1 * p0y + 3 * t1 * t1 * t * p1y + 3 * t1 * t * t * p2y + t * t * t * p3y;
            
            graphics.fill((int) prevX, (int) prevY, (int) x, (int) (y + 1), color);
            
            double dist = Math.sqrt((x - prevX) * (x - prevX) + (y - prevY) * (y - prevY));
            totalLen += dist;
            lut[i] = totalLen;
            pts.add(new Vec2d(x, y));
            
            prevX = x;
            prevY = y;
        }

        // Particle flows
        if (totalLen > 0) {
            long time = System.currentTimeMillis();
            double speed = 40.0;
            double spacing = 80.0;
            
            double baseOffset = (time / 1000.0 * speed) % spacing;
            for (double d = baseOffset; d < totalLen; d += spacing) {
                Vec2d pt = getPointAt(pts, lut, d);
                graphics.fill((int) pt.x - 1, (int) pt.y - 1, (int) pt.x + 2, (int) pt.y + 2, 0xFFFFFFFF);
            }
        }
    }

    private Vec2d getPointAt(List<Vec2d> points, double[] lut, double distance) {
        if (distance <= 0) return points.get(0);
        if (distance >= lut[lut.length - 1]) return points.get(points.size() - 1);

        int low = 0;
        int high = lut.length - 1;
        while (low < high - 1) {
            int mid = (low + high) / 2;
            if (lut[mid] < distance) {
                low = mid;
            } else {
                high = mid;
            }
        }

        double d0 = lut[low];
        double d1 = lut[high];
        double segmentFraction = (distance - d0) / (d1 - d0);

        Vec2d p0 = points.get(low);
        Vec2d p1 = points.get(high);

        double x = p0.x + segmentFraction * (p1.x - p0.x);
        double y = p0.y + segmentFraction * (p1.y - p0.y);
        return new Vec2d(x, y);
    }

    private void renderBranchNodes(GuiGraphics graphics, double mx, double my) {
        drawMountCard(graphics, 0, 0, baseTemplateId, Component.translatable("gui.rpg_mounts.evolution.parent").getString(), true, false, mx, my);

        EvolutionTreeManager.EvolutionTree tree = EvolutionTreeManager.loadedTrees.get(baseTemplateId);
        if (tree == null) return;

        List<EvolutionPathInfo> paths = EvolutionAPI.getProvider().getEvolutionPaths(mount);

        for (EvolutionTreeManager.EvolutionBranch branch : tree.branches) {
            EvolutionPathInfo pathInfo = null;
            for (EvolutionPathInfo p : paths) {
                if (p.targetTemplateId.equals(branch.targetId)) {
                    pathInfo = p;
                    break;
                }
            }

            boolean isAvailable = pathInfo != null && pathInfo.isUnlocked;
            boolean isLocked = pathInfo == null || !pathInfo.isUnlocked;

            drawMountCard(graphics, branch.xCoord, branch.yCoord, branch.targetId, branch.displayName, isAvailable, isLocked, mx, my);
        }
    }

    private void drawMountCard(GuiGraphics graphics, int cx, int cy, String templateId, String name, boolean unlocked, boolean locked, double mx, double my) {
        int x = cx - CARD_W / 2;
        int y = cy - CARD_H / 2;

        boolean hovered = mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H;
        int borderCol = unlocked ? 0xFF3bb053 : (hovered ? 0xFFffd700 : 0xFF2E3142);
        if (locked) borderCol = 0xFF2c2d33;

        String category = "GROUND";
        MountData template = MountRegistry.getTemplate(templateId);
        if (template != null && template.category != null) {
            category = template.category;
        }

        if (templateId.equals(baseTemplateId)) {
            UIHelper.drawBeveledPanel(graphics, x - 2, y - 2, CARD_W + 4, CARD_H + 4, 0xFFffd700, UIHelper.COLOR_OBSIDIAN);
        } else if (category.equalsIgnoreCase("flying")) {
            graphics.pose().pushPose();
            graphics.pose().translate(cx, cy, 0.0);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45));
            UIHelper.drawBeveledPanel(graphics, -CARD_W / 2, -CARD_H / 2, CARD_W, CARD_H, borderCol, UIHelper.COLOR_CHARCOAL);
            graphics.pose().popPose();
        } else if (category.equalsIgnoreCase("aquatic")) {
            UIHelper.drawBeveledPanel(graphics, x + 2, y + 2, CARD_W - 4, CARD_H - 4, borderCol, UIHelper.COLOR_CHARCOAL);
        } else {
            UIHelper.drawBeveledPanel(graphics, x, y, CARD_W, CARD_H, borderCol, UIHelper.COLOR_CHARCOAL);
        }

        if (unlocked && !templateId.equals(baseTemplateId)) {
            float pulse = (float) Math.sin((System.currentTimeMillis() % 1500) / 1500.0 * Math.PI * 2) * 0.5f + 0.5f;
            int pulseCol = UIHelper.blendColors(0x1Fffd700, 0x7Fffd700, pulse);
            if (category.equalsIgnoreCase("flying")) {
                graphics.pose().pushPose();
                graphics.pose().translate(cx, cy, 0.0);
                graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45));
                UIHelper.drawOutline(graphics, -CARD_W / 2 - 1, -CARD_H / 2 - 1, CARD_W + 2, CARD_H + 2, pulseCol);
                graphics.pose().popPose();
            } else if (category.equalsIgnoreCase("aquatic")) {
                UIHelper.drawOutline(graphics, x + 1, y + 1, CARD_W - 2, CARD_H - 2, pulseCol);
            } else {
                UIHelper.drawOutline(graphics, x - 1, y - 1, CARD_W + 2, CARD_H + 2, pulseCol);
            }
        }

        ItemStack iconStack = new ItemStack(ddraig.net.rpgmounts.registry.ModItems.WHISTLE.get());
        EvolutionTreeManager.EvolutionTree tree = EvolutionTreeManager.loadedTrees.get(baseTemplateId);
        if (tree != null) {
            for (EvolutionTreeManager.EvolutionBranch branch : tree.branches) {
                if (branch.targetId.equals(templateId) && branch.conditions != null && !branch.conditions.items.isEmpty()) {
                    String itemId = branch.conditions.items.get(0).id;
                    iconStack = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(itemId)));
                    break;
                }
            }
        }

        graphics.renderItem(iconStack, cx - 8, cy - 8);
        graphics.drawCenteredString(font, name, cx, cy + CARD_H / 2 + 4, locked ? 0xFF606475 : 0xFFe6e6fa);
    }

    private void renderHUDDrawer(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selectedBranch == null) return;
        
        int startX = width - DRAWER_W;
        UIHelper.drawBeveledPanel(graphics, startX, 0, DRAWER_W, height, 0xFFffd700, UIHelper.COLOR_OBSIDIAN);
        
        graphics.drawString(font, selectedBranch.displayName, startX + 8, 12, 0xFFffd700);
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.requirements.title"), startX + 8, 30, 0xFFaaaaaa);

        int yOffset = 45;
        if (selectedPathInfo != null) {
            if (selectedPathInfo.isUnlocked) {
                graphics.drawString(font, Component.translatable("rpg_mounts.evolution.requirements.met"), startX + 8, yOffset, 0xFFFFFFFF);
            } else {
                for (String req : selectedPathInfo.missingRequirements) {
                    Component comp = formatRequirement(req);
                    graphics.drawString(font, comp, startX + 8, yOffset, 0xFFd93b3b);
                    yOffset += 12;
                }
            }
        }

        // Evolve Button
        boolean buttonHovered = mouseX >= startX + 8 && mouseX < width - 8 && mouseY >= height - 32 && mouseY < height - 8;
        int btnCol = selectedPathInfo != null && selectedPathInfo.isUnlocked ? 0xFF3bb053 : 0xFF545763;
        UIHelper.drawShadedButton(graphics, startX + 8, height - 32, DRAWER_W - 16, 24, buttonHovered, btnCol);
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.action.evolve"), startX + 28, height - 24, selectedPathInfo != null && selectedPathInfo.isUnlocked ? 0xFFFFFFFF : 0xFFaaaaaa);
    }

    private Component formatRequirement(String raw) {
        if (raw.startsWith("level:")) {
            int req = Integer.parseInt(raw.substring(6));
            int current = 1;
            var uData = DatabaseManager.unlockedMountsCache.get(mount.getOwnerUuid()) != null ?
                        DatabaseManager.unlockedMountsCache.get(mount.getOwnerUuid()).get(mount.getInstanceId()) : null;
            if (uData != null) current = uData.level;
            return Component.translatable("rpg_mounts.evolution.requirements.level", req, current);
        }
        if (raw.startsWith("bonding:")) {
            int req = Integer.parseInt(raw.substring(8));
            int current = 0;
            var uData = DatabaseManager.unlockedMountsCache.get(mount.getOwnerUuid()) != null ?
                        DatabaseManager.unlockedMountsCache.get(mount.getOwnerUuid()).get(mount.getInstanceId()) : null;
            if (uData != null) current = uData.bondingScore;
            return Component.translatable("rpg_mounts.evolution.requirements.bonding", req, current);
        }
        if (raw.equals("requires_chroma")) {
            return Component.translatable("rpg_mounts.evolution.requirements.chroma");
        }
        if (raw.startsWith("dimension:")) {
            return Component.translatable("rpg_mounts.evolution.requirements.dimension", raw.substring(10));
        }
        if (raw.startsWith("biome:")) {
            return Component.translatable("rpg_mounts.evolution.requirements.biome", raw.substring(6));
        }
        if (raw.startsWith("item:")) {
            String[] split = raw.split(":");
            return Component.translatable("rpg_mounts.evolution.requirements.item", split[1].replace("minecraft:", ""), Integer.parseInt(split[2]));
        }
        if (raw.startsWith("status_effect:")) {
            String[] split = raw.split(":");
            return Component.translatable("rpg_mounts.evolution.requirements.status_effect", split[1].replace("minecraft:", ""), Integer.parseInt(split[2]));
        }
        if (raw.startsWith("mob_kills:")) {
            String[] split = raw.split(":");
            return Component.translatable("rpg_mounts.evolution.requirements.mob_kills", split[1].replace("minecraft:", ""), Integer.parseInt(split[2]));
        }
        if (raw.startsWith("moon_phase:")) {
            return Component.translatable("rpg_mounts.evolution.requirements.moon_phase", raw.substring(11));
        }
        if (raw.equals("excluded_by_mutex")) {
            return Component.translatable("rpg_mounts.evolution.warning.mutex_short");
        }
        return Component.literal("• " + raw);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= width - DRAWER_W) {
            if (selectedBranch != null && selectedPathInfo != null && selectedPathInfo.isUnlocked) {
                if (mouseY >= height - 32 && mouseY < height - 8 && mouseX >= width - DRAWER_W + 8 && mouseX < width - 8) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeUtf(mount.getInstanceId());
                    buf.writeUtf(selectedBranch.targetId);
                    NetworkManager.sendToServer(EvolutionPackets.C2S_REQUEST_BRANCH_EVOLVE, buf);
                    this.minecraft.setScreen(null);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        double canvasMouseX = (mouseX - width / 2.0 - panX) / zoom;
        double canvasMouseY = (mouseY - height / 2.0 - panY) / zoom;

        EvolutionTreeManager.EvolutionTree tree = EvolutionTreeManager.loadedTrees.get(baseTemplateId);
        if (tree != null) {
            for (EvolutionTreeManager.EvolutionBranch branch : tree.branches) {
                int x = branch.xCoord - CARD_W / 2;
                int y = branch.yCoord - CARD_H / 2;
                if (canvasMouseX >= x && canvasMouseX < x + CARD_W && canvasMouseY >= y && canvasMouseY < y + CARD_H) {
                    selectedBranch = branch;
                    
                    List<EvolutionPathInfo> paths = EvolutionAPI.getProvider().getEvolutionPaths(mount);
                    for (EvolutionPathInfo p : paths) {
                        if (p.targetTemplateId.equals(branch.targetId)) {
                            selectedPathInfo = p;
                            break;
                        }
                    }
                    
                    focusOnNode(branch.xCoord, branch.yCoord);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (mouseX < width - DRAWER_W) {
            this.panX += dragX;
            this.panY += dragY;
            this.velX = dragX;
            this.velY = dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX < width - DRAWER_W) {
            double oldZoom = zoom;
            zoom = Math.max(0.5, Math.min(2.0, zoom + amount * 0.1));
            
            double mouseCanvasX = (mouseX - width / 2.0 - panX) / oldZoom;
            double mouseCanvasY = (mouseY - height / 2.0 - panY) / oldZoom;
            panX = mouseX - width / 2.0 - mouseCanvasX * zoom;
            panY = mouseY - height / 2.0 - mouseCanvasY * zoom;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 32) { // Spacebar
            focusOnNode(0, 0);
            return true;
        }
        if (keyCode == 45) { // Minus (-)
            double oldZoom = zoom;
            zoom = Math.max(0.5, Math.min(2.0, zoom - 0.1));
            double mouseCanvasX = (width / 2.0 - width / 2.0 - panX) / oldZoom;
            double mouseCanvasY = (height / 2.0 - height / 2.0 - panY) / oldZoom;
            panX = width / 2.0 - width / 2.0 - mouseCanvasX * zoom;
            panY = height / 2.0 - height / 2.0 - mouseCanvasY * zoom;
            return true;
        }
        if (keyCode == 61) { // Equal (=)
            double oldZoom = zoom;
            zoom = Math.max(0.5, Math.min(2.0, zoom + 0.1));
            double mouseCanvasX = (width / 2.0 - width / 2.0 - panX) / oldZoom;
            double mouseCanvasY = (height / 2.0 - height / 2.0 - panY) / oldZoom;
            panX = width / 2.0 - width / 2.0 - mouseCanvasX * zoom;
            panY = height / 2.0 - height / 2.0 - mouseCanvasY * zoom;
            return true;
        }
        if (keyCode == 48 && Screen.hasControlDown()) { // Ctrl + 0
            this.startPanX = panX;
            this.startPanY = panY;
            this.startZoom = zoom;
            this.targetPanX = panX;
            this.targetPanY = panY;
            this.targetZoom = 1.0;
            this.easingStartTime = System.currentTimeMillis();
            this.isEasing = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void focusOnNode(int cx, int cy) {
        this.startPanX = panX;
        this.startPanY = panY;
        this.startZoom = zoom;
        
        this.targetPanX = -cx;
        this.targetPanY = -cy;
        this.targetZoom = 1.0;
        
        this.easingStartTime = System.currentTimeMillis();
        this.isEasing = true;
    }

    public static class Vec2d {
        final double x;
        final double y;
        Vec2d(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}
