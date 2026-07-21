package ddraig.net.rpgmounts.evolution.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import ddraig.net.rpgmounts.RPGMounts;
import ddraig.net.rpgmounts.client.gui.UIHelper;
import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.evolution.config.EvolutionTreeManager;
import ddraig.net.rpgmounts.evolution.network.EvolutionPackets;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Advanced Visual Evolution Tree configuration Editor for Administrators.
 * Implements mouse panning/zooms, coordinate transformations, snap-to-grid, 
 * multi-selection translation matrices, context menus, undo/redo command patterns, 
 * reroute pin splines, and change tracking indicators.
 */
public class AddonEvolutionEditorScreen extends Screen {

    // Workspace Dimensions
    private static final int SIDEBAR_W = 120;
    private static final int CARD_W = 32;
    private static final int CARD_H = 32;

    // Active configuration file state
    private String activeBaseMountId = "";
    private EvolutionTreeManager.EvolutionTree activeTree = null;
    private boolean isDirty = false;

    // Viewport Panning & Zoom matrices
    private double panX = 0;
    private double panY = 0;
    private double zoom = 1.0;
    private double dragStartX, dragStartY;
    private boolean isDraggingCanvas = false;

    // Sidebar scrolling state
    private int scrollOffset = 0;
    private final List<String> loadedMountTemplateKeys = new ArrayList<>();

    // Selection matrices
    private final List<EvolutionTreeManager.EvolutionBranch> selectedNodes = new ArrayList<>();
    private EvolutionTreeManager.EvolutionBranch activeDraggingNode = null;
    private int dragNodeOffsetX, dragNodeOffsetY;
    
    // Box selection coordinates
    private boolean isBoxSelecting = false;
    private double boxSelectStartX, boxSelectStartY;
    private double boxSelectEndX, boxSelectEndY;

    // Context menu popups state
    private boolean showContextMenu = false;
    private int contextMenuX, contextMenuY;
    private EvolutionTreeManager.EvolutionBranch contextMenuTargetNode = null;
    private final List<ContextMenuItem> contextMenuItems = new ArrayList<>();

    // Node dependency linking state
    private EvolutionTreeManager.EvolutionBranch linkParentCandidate = null;

    // History registers (Undo / Redo stacks)
    private final Stack<IEditorCommand> undoStack = new Stack<>();
    private final Stack<IEditorCommand> redoStack = new Stack<>();
    private static final int MAX_HISTORY = 128;

    // Exit confirmation states
    private boolean showUnsavedChangesModal = false;

    // Input Dialog fields
    private boolean showInputDialog = false;
    private String inputDialogTitle = "";
    private String inputDialogValue = "";
    private java.util.function.Consumer<String> inputCallback = null;

    // Catalyst Selector fields
    private boolean showCatalystSelector = false;
    private String catalystSearchQuery = "";
    private int catalystScrollOffset = 0;
    private final List<String> allRegisteredItemIds = new ArrayList<>();
    private final List<String> filteredCatalystIds = new ArrayList<>();
    private java.util.function.Consumer<String> catalystCallback = null;

    // Details Popup fields
    private boolean showDetailsPopup = false;
    private EvolutionTreeManager.EvolutionBranch detailsTargetBranch = null;
    private final List<DetailRow> detailRows = new ArrayList<>();

    // Drag-and-drop template card state
    private boolean isDraggingTemplate = false;
    private String draggingTemplateId = null;

    // Reroute node dragging state
    private EvolutionTreeManager.EvolutionBranch activeDraggingRerouteBranch = null;
    private int activeDraggingRerouteIndex = -1;

    // Click timing fields for double click
    private long lastClickTime = 0;
    private double lastClickX = 0;
    private double lastClickY = 0;

    // Move command start coordinates mapping
    private final Map<String, Vec2iOld> dragStartCoords = new HashMap<>();
    private int dragRerouteStartCoordsX = 0;
    private int dragRerouteStartCoordsY = 0;
    
    // Template Selector fields
    private boolean showTemplateSelector = false;
    private String templateSearchQuery = "";
    private int templateScrollOffset = 0;
    private double canvasSpawnX = 0;
    private double canvasSpawnY = 0;
    private final List<String> filteredTemplateIds = new ArrayList<>();

    public AddonEvolutionEditorScreen() {
        super(Component.translatable("rpg_mounts.evolution.editor.title"));
        loadedMountTemplateKeys.addAll(MountRegistry.loadedTemplates.keySet());
        if (!loadedMountTemplateKeys.isEmpty()) {
            loadTree(loadedMountTemplateKeys.get(0));
        }

        // Cache all item IDs once for catalyst search
        for (ResourceLocation loc : BuiltInRegistries.ITEM.keySet()) {
            allRegisteredItemIds.add(loc.toString());
        }
        filteredCatalystIds.addAll(allRegisteredItemIds);
    }

    private void openInputDialog(String title, String initialVal, java.util.function.Consumer<String> callback) {
        this.inputDialogTitle = title;
        this.inputDialogValue = initialVal;
        this.inputCallback = callback;
        this.showInputDialog = true;
    }

    private void openCatalystSelector(java.util.function.Consumer<String> callback) {
        this.catalystSearchQuery = "";
        updateCatalystFilter();
        this.catalystCallback = callback;
        this.showCatalystSelector = true;
    }

    private void updateCatalystFilter() {
        filteredCatalystIds.clear();
        String q = catalystSearchQuery.toLowerCase();
        for (String id : allRegisteredItemIds) {
            if (id.toLowerCase().contains(q)) {
                filteredCatalystIds.add(id);
            }
        }
        catalystScrollOffset = 0;
    }

    private void openTemplateSelector(double canvasX, double canvasY) {
        this.canvasSpawnX = canvasX;
        this.canvasSpawnY = canvasY;
        this.templateSearchQuery = "";
        updateTemplateFilter();
        this.showTemplateSelector = true;
    }

    private void updateTemplateFilter() {
        filteredTemplateIds.clear();
        String q = templateSearchQuery.toLowerCase();
        for (String id : MountRegistry.loadedTemplates.keySet()) {
            boolean exists = false;
            if (activeTree != null) {
                for (EvolutionTreeManager.EvolutionBranch b : activeTree.branches) {
                    if (b.targetId.equals(id)) {
                        exists = true;
                        break;
                    }
                }
            }
            if (!exists && id.toLowerCase().contains(q)) {
                filteredTemplateIds.add(id);
            }
        }
        templateScrollOffset = 0;
    }

    public void refreshTrees() {
        if (!activeBaseMountId.isEmpty()) {
            loadTree(activeBaseMountId);
        }
    }

    private void loadTree(String baseMountId) {
        this.activeBaseMountId = baseMountId;
        this.selectedNodes.clear();
        this.undoStack.clear();
        this.redoStack.clear();
        this.isDirty = false;

        EvolutionTreeManager.EvolutionTree tree = EvolutionTreeManager.loadedTrees.get(baseMountId);
        if (tree != null) {
            // Clone tree configurations structure for editing safety
            String json = new Gson().toJson(tree);
            this.activeTree = new Gson().fromJson(json, EvolutionTreeManager.EvolutionTree.class);
        } else {
            this.activeTree = new EvolutionTreeManager.EvolutionTree();
            this.activeTree.mountId = baseMountId;
        }

        // Auto center coordinates
        this.panX = 0;
        this.panY = 0;
        this.zoom = 1.0;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render foundational dark obsidian workspace background
        graphics.fill(0, 0, width, height, 0xFF12131A);

        // 1. Draw Panning dotted grid
        drawGridOverlay(graphics);

        // Compute localized canvas coordinates
        double canvasMouseX = (mouseX - SIDEBAR_W - (width - SIDEBAR_W) / 2.0 - panX) / zoom;
        double canvasMouseY = (mouseY - (height) / 2.0 - panY) / zoom;

        // 2. Render scaling matrix viewports
        graphics.pose().pushPose();
        graphics.pose().translate(SIDEBAR_W + (width - SIDEBAR_W) / 2.0 + panX, height / 2.0 + panY, 0.0);
        graphics.pose().scale((float) zoom, (float) zoom, 1.0f);

        renderSplines(graphics);
        renderNodes(graphics, canvasMouseX, canvasMouseY);

        graphics.pose().popPose();

        // 3. Render box selection overlay if active
        if (isBoxSelecting) {
            drawBoxSelectionOverlay(graphics);
        }

        // 4. Render Sidebar panel scrollable mount templates list
        renderSidebar(graphics, mouseX, mouseY);

        // 5. Render Top Toolbar button bars
        renderToolbar(graphics, mouseX, mouseY);

        // 6. Render Context Menu Popups
        if (showContextMenu) {
            renderContextMenu(graphics, mouseX, mouseY);
        }

        // 7. Render Unsaved changes Warning Dialog Overlay
        if (showUnsavedChangesModal) {
            renderUnsavedChangesModal(graphics, mouseX, mouseY);
        }

        // 8. Render Input Dialog Overlay
        if (showInputDialog) {
            renderInputDialog(graphics, mouseX, mouseY);
        }

        // 9. Render Catalyst Selector Overlay
        if (showCatalystSelector) {
            renderCatalystSelector(graphics, mouseX, mouseY);
        }

        if (showTemplateSelector) {
            renderTemplateSelector(graphics, mouseX, mouseY);
        }

        // 10. Render Requirement Details Checklist Popup
        if (showDetailsPopup) {
            renderDetailsPopup(graphics, mouseX, mouseY);
        }

        // 11. Render Drag-and-drop thumbnail
        if (isDraggingTemplate && draggingTemplateId != null) {
            int tx = mouseX - CARD_W / 2;
            int ty = mouseY - CARD_H / 2;
            RenderSystem.enableBlend();
            UIHelper.drawBeveledPanel(graphics, tx, ty, CARD_W, CARD_H, 0x88ffd700, 0x881C1D26);
            graphics.drawString(font, draggingTemplateId.replace("rpg_mounts:", ""), tx + 8, ty + 12, 0x88e6e6fa);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawGridOverlay(GuiGraphics graphics) {
        int dotSpacing = 16;
        int gridColor = UIHelper.blendColors(0x11000000, 0x11ffffff, (float) Math.max(0.0, Math.min(1.0, (zoom - 0.5) / 1.5)));
        
        double startX = SIDEBAR_W + ((( - panX - (width - SIDEBAR_W) / 2.0) % dotSpacing));
        double startY = (( - panY - height / 2.0) % dotSpacing);

        RenderSystem.enableBlend();
        for (double x = startX; x < width; x += dotSpacing * zoom) {
            for (double y = startY; y < height; y += dotSpacing * zoom) {
                graphics.fill((int) x, (int) y, (int) (x + 1.5), (int) (y + 1.5), gridColor);
            }
        }
    }

    private void renderSplines(GuiGraphics graphics) {
        if (activeTree == null) return;

        // Draw parent-child connections
        for (EvolutionTreeManager.EvolutionBranch branch : activeTree.branches) {
            int sx = 0, sy = 0;
            if (branch.parentId != null && !branch.parentId.isEmpty()) {
                for (EvolutionTreeManager.EvolutionBranch other : activeTree.branches) {
                    if (other.targetId.equals(branch.parentId)) {
                        sx = other.xCoord;
                        sy = other.yCoord;
                        break;
                    }
                }
            }
            int color = selectedNodes.contains(branch) ? 0xFF3bb053 : 0xFFffd700;
            
            int prevX = sx;
            int prevY = sy;
            for (EvolutionTreeManager.Vec2i pt : branch.reroutePoints) {
                drawBezierSpline(graphics, prevX, prevY, pt.x, pt.y, color);
                prevX = pt.x;
                prevY = pt.y;
            }
            drawBezierSpline(graphics, prevX, prevY, branch.xCoord, branch.yCoord, color);
        }

        // Draw mutual exclusion lines in Red
        for (EvolutionTreeManager.EvolutionBranch branch : activeTree.branches) {
            for (String exId : branch.excludes) {
                int tx = 0, ty = 0;
                boolean found = false;
                if (exId.equals(activeBaseMountId)) {
                    tx = 0; ty = 0;
                    found = true;
                } else {
                    for (EvolutionTreeManager.EvolutionBranch other : activeTree.branches) {
                        if (other.targetId.equals(exId)) {
                            tx = other.xCoord;
                            ty = other.yCoord;
                            found = true;
                            break;
                        }
                    }
                }
                if (found) {
                    drawBezierSpline(graphics, branch.xCoord, branch.yCoord, tx, ty, 0xFFd93b3b);
                }
            }
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

    private void renderNodes(GuiGraphics graphics, double mx, double my) {
        // Draw Root node at (0, 0)
        drawNodeCard(graphics, 0, 0, activeBaseMountId, "Root Mount", true, mx, my);

        if (activeTree == null) return;
        for (EvolutionTreeManager.EvolutionBranch branch : activeTree.branches) {
            boolean isSelected = selectedNodes.contains(branch);
            drawNodeCard(graphics, branch.xCoord, branch.yCoord, branch.targetId, branch.displayName, isSelected, mx, my);
        }

        // Draw reroute points as small circular pegs
        for (EvolutionTreeManager.EvolutionBranch branch : activeTree.branches) {
            for (EvolutionTreeManager.Vec2i pt : branch.reroutePoints) {
                boolean hovered = mx >= pt.x - 4 && mx <= pt.x + 4 && my >= pt.y - 4 && my <= pt.y + 4;
                int color = hovered ? 0xFFffd700 : 0xFF2E3142;
                graphics.fill(pt.x - 3, pt.y - 3, pt.x + 4, pt.y + 4, color);
                graphics.fill(pt.x - 2, pt.y - 2, pt.x + 3, pt.y + 3, 0xFFffd700);
            }
        }
    }

    private void drawNodeCard(GuiGraphics graphics, int cx, int cy, String id, String name, boolean selected, double mx, double my) {
        int x = cx - CARD_W / 2;
        int y = cy - CARD_H / 2;

        boolean hovered = mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H;
        int borderCol = selected ? 0xFF3bb053 : (hovered ? 0xFFffd700 : 0xFF2E3142);

        String category = "GROUND";
        MountData template = MountRegistry.getTemplate(id);
        if (template != null && template.category != null) {
            category = template.category;
        }

        if (id.equals(activeBaseMountId)) {
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

        ItemStack iconStack = new ItemStack(ddraig.net.rpgmounts.registry.ModItems.WHISTLE.get());
        if (activeTree != null) {
            for (EvolutionTreeManager.EvolutionBranch branch : activeTree.branches) {
                if (branch.targetId.equals(id) && branch.conditions != null && !branch.conditions.items.isEmpty()) {
                    String itemId = branch.conditions.items.get(0).id;
                    iconStack = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(itemId)));
                    break;
                }
            }
        }

        graphics.renderItem(iconStack, cx - 8, cy - 8);
        graphics.drawCenteredString(font, name, cx, cy + CARD_H / 2 + 4, 0xFFe6e6fa);
    }

    private void drawBoxSelectionOverlay(GuiGraphics graphics) {
        int minX = (int) Math.min(boxSelectStartX, boxSelectEndX);
        int maxX = (int) Math.max(boxSelectStartX, boxSelectEndX);
        int minY = (int) Math.min(boxSelectStartY, boxSelectEndY);
        int maxY = (int) Math.max(boxSelectStartY, boxSelectEndY);

        graphics.fill(minX, minY, maxX, maxY, 0x1A3bb053);
        UIHelper.drawOutline(graphics, minX, minY, maxX - minX, maxY - minY, 0xFF3bb053);
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        UIHelper.drawBeveledPanel(graphics, 0, 0, SIDEBAR_W, height, 0xFF2E3142, UIHelper.COLOR_OBSIDIAN);
        
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.mounts"), 8, 8, 0xFFffd700);
        
        int yOffset = 24;
        for (int i = scrollOffset; i < loadedMountTemplateKeys.size(); i++) {
            if (yOffset > height - 20) break;
            
            String id = loadedMountTemplateKeys.get(i);
            boolean hovered = mouseX >= 4 && mouseX < SIDEBAR_W - 4 && mouseY >= yOffset && mouseY < yOffset + 20;
            boolean active = id.equals(activeBaseMountId);
            
            int border = active ? 0xFF3bb053 : (hovered ? 0xFFffd700 : 0xFF2E3142);
            UIHelper.drawBeveledPanel(graphics, 4, yOffset, SIDEBAR_W - 8, 18, border, UIHelper.COLOR_CHARCOAL);
            graphics.drawString(font, id.replace("rpg_mounts:", ""), 10, yOffset + 5, active ? 0xFF3bb053 : 0xFFe6e6fa);
            
            yOffset += 22;
        }
    }

    private void renderToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int toolbarX = SIDEBAR_W + 8;
        int toolbarY = 6;
        
        // Save Button
        boolean saveHover = mouseX >= toolbarX && mouseX < toolbarX + 48 && mouseY >= toolbarY && mouseY < toolbarY + 18;
        UIHelper.drawShadedButton(graphics, toolbarX, toolbarY, 48, 18, saveHover, 0xFF3bb053);
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.save"), toolbarX + 12, toolbarY + 5, 0xFFFFFFFF);

        // Auto Layout Button
        toolbarX += 54;
        boolean layoutHover = mouseX >= toolbarX && mouseX < toolbarX + 72 && mouseY >= toolbarY && mouseY < toolbarY + 18;
        UIHelper.drawShadedButton(graphics, toolbarX, toolbarY, 72, 18, layoutHover, 0xFF4b7ecb);
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.layout"), toolbarX + 6, toolbarY + 5, 0xFFFFFFFF);

        // Title display
        String dirtyIndicator = isDirty ? "*" : "";
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.active", activeBaseMountId, dirtyIndicator).getString(), width - 200, 10, 0xFFe6e6fa);
    }

    private void renderContextMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        int menuW = 120;
        int menuH = contextMenuItems.size() * 16 + 4;
        
        UIHelper.drawBeveledPanel(graphics, contextMenuX, contextMenuY, menuW, menuH, 0xFFffd700, UIHelper.COLOR_OBSIDIAN);
        
        int yOffset = contextMenuY + 2;
        for (ContextMenuItem item : contextMenuItems) {
            boolean hovered = mouseX >= contextMenuX && mouseX < contextMenuX + menuW && mouseY >= yOffset && mouseY < yOffset + 14;
            if (hovered) {
                graphics.fill(contextMenuX + 2, yOffset, contextMenuX + menuW - 2, yOffset + 14, 0x36ffffff);
            }
            graphics.drawString(font, item.label, contextMenuX + 6, yOffset + 3, 0xFFe6e6fa);
            yOffset += 16;
        }
    }

    private void renderUnsavedChangesModal(GuiGraphics graphics, int mouseX, int mouseY) {
        int modalW = 200;
        int modalH = 80;
        int mx = (width - modalW) / 2;
        int my = (height - modalH) / 2;

        UIHelper.drawBeveledPanel(graphics, mx, my, modalW, modalH, 0xFFd93b3b, UIHelper.COLOR_OBSIDIAN);
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.unsaved"), mx + 16, my + 12, 0xFFd93b3b);
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.discard"), mx + 16, my + 26, 0xFFaaaaaa);

        boolean yesHover = mouseX >= mx + 16 && mouseX < mx + 80 && mouseY >= my + 48 && mouseY < my + 68;
        UIHelper.drawShadedButton(graphics, mx + 16, my + 48, 64, 20, yesHover, 0xFFd93b3b);
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.discard_btn"), mx + 26, my + 54, 0xFFFFFFFF);

        boolean noHover = mouseX >= mx + 120 && mouseX < mx + 184 && mouseY >= my + 48 && mouseY < my + 68;
        UIHelper.drawShadedButton(graphics, mx + 120, my + 48, 64, 20, noHover, 0xFF2E3142);
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.cancel_btn"), mx + 134, my + 54, 0xFFFFFFFF);
    }

    private void renderInputDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        int modalW = 220;
        int modalH = 80;
        int mx = (width - modalW) / 2;
        int my = (height - modalH) / 2;

        UIHelper.drawBeveledPanel(graphics, mx, my, modalW, modalH, 0xFFffd700, UIHelper.COLOR_OBSIDIAN);
        graphics.drawString(font, inputDialogTitle, mx + 12, my + 12, 0xFFffd700);
        
        UIHelper.drawRecessedSlot(graphics, mx + 12, my + 30, modalW - 24, 20, 0xFF2E3142, UIHelper.COLOR_CHARCOAL);
        graphics.drawString(font, inputDialogValue + (System.currentTimeMillis() % 1000 < 500 ? "_" : ""), mx + 18, my + 36, 0xFFFFFFFF);
        
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.confirm_hint"), mx + 12, my + 60, 0xFF888888);
    }

    private void renderCatalystSelector(GuiGraphics graphics, int mouseX, int mouseY) {
        int modalW = 200;
        int modalH = 200;
        int mx = (width - modalW) / 2;
        int my = (height - modalH) / 2;

        UIHelper.drawBeveledPanel(graphics, mx, my, modalW, modalH, 0xFFffd700, UIHelper.COLOR_OBSIDIAN);
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.select_catalyst"), mx + 12, my + 12, 0xFFffd700);

        // Search Bar
        UIHelper.drawRecessedSlot(graphics, mx + 12, my + 26, modalW - 24, 18, 0xFF2E3142, UIHelper.COLOR_CHARCOAL);
        graphics.drawString(font, Component.translatable("gui.rpg_mounts.creator.placeholder.search").getString() + ": " + catalystSearchQuery + (System.currentTimeMillis() % 1000 < 500 ? "_" : ""), mx + 16, my + 31, 0xFFFFFFFF);

        int gridX = mx + 12;
        int gridY = my + 50;
        int cols = 5;
        int rows = 5;
        int slotSize = 24;

        int index = catalystScrollOffset;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int sx = gridX + c * slotSize;
                int sy = gridY + r * slotSize;

                if (index < filteredCatalystIds.size()) {
                    String itemId = filteredCatalystIds.get(index);
                    boolean hovered = mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize;
                    
                    int border = hovered ? 0xFFffd700 : 0xFF2E3142;
                    UIHelper.drawRecessedSlot(graphics, sx, sy, slotSize, slotSize, border, UIHelper.COLOR_CHARCOAL);
                    
                    ResourceLocation loc = new ResourceLocation(itemId);
                    Item item = BuiltInRegistries.ITEM.get(loc);
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        graphics.renderItem(new ItemStack(item), sx + 4, sy + 4);
                    }
                    index++;
                } else {
                    UIHelper.drawRecessedSlot(graphics, sx, sy, slotSize, slotSize, 0xFF2E3142, UIHelper.COLOR_OBSIDIAN);
                }
            }
        }
        
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.scroll_hint"), mx + 12, my + 185, 0xFF888888);
    }

    private void renderTemplateSelector(GuiGraphics graphics, int mouseX, int mouseY) {
        int modalW = 180;
        int modalH = 180;
        int mx = (width - modalW) / 2;
        int my = (height - modalH) / 2;

        UIHelper.drawBeveledPanel(graphics, mx, my, modalW, modalH, 0xFFffd700, UIHelper.COLOR_OBSIDIAN);
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.select_template").getString(), mx + 12, my + 10, 0xFFffd700);

        // Search Bar
        UIHelper.drawRecessedSlot(graphics, mx + 12, my + 24, modalW - 24, 18, 0xFF2E3142, UIHelper.COLOR_CHARCOAL);
        graphics.drawString(font, Component.translatable("gui.rpg_mounts.creator.placeholder.search").getString() + ": " + templateSearchQuery + (System.currentTimeMillis() % 1000 < 500 ? "_" : ""), mx + 16, my + 29, 0xFFFFFFFF);

        int startY = my + 48;
        int rowH = 18;
        int maxRows = 6;

        int index = templateScrollOffset;
        for (int i = 0; i < maxRows; i++) {
            int ry = startY + i * rowH;
            if (index < filteredTemplateIds.size()) {
                String templateId = filteredTemplateIds.get(index);
                boolean hovered = mouseX >= mx + 12 && mouseX < mx + modalW - 12 && mouseY >= ry && mouseY < ry + rowH;
                int border = hovered ? 0xFFffd700 : 0xFF2E3142;
                UIHelper.drawBeveledPanel(graphics, mx + 12, ry, modalW - 24, rowH - 2, border, UIHelper.COLOR_CHARCOAL);
                
                graphics.renderItem(new ItemStack(ddraig.net.rpgmounts.registry.ModItems.WHISTLE.get()), mx + 16, ry + 1);
                
                String label = templateId.replace("rpg_mounts:", "");
                if (label.length() > 18) label = label.substring(0, 16) + "..";
                graphics.drawString(font, label, mx + 36, ry + 3, 0xFFe6e6fa);
                index++;
            } else {
                UIHelper.drawRecessedSlot(graphics, mx + 12, ry, modalW - 24, rowH - 2, 0xFF2E3142, UIHelper.COLOR_OBSIDIAN);
            }
        }

        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.scroll_hint").getString(), mx + 12, my + 165, 0xFF888888);
    }

    private void renderDetailsPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        int modalW = 240;
        int modalH = 180;
        int mx = (width - modalW) / 2;
        int my = (height - modalH) / 2;

        UIHelper.drawBeveledPanel(graphics, mx, my, modalW, modalH, 0xFF3bb053, UIHelper.COLOR_OBSIDIAN);
        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.checklist_title"), mx + 12, my + 12, 0xFF3bb053);

        int listX = mx + 12;
        int listY = my + 30;
        int rowH = 15;

        for (int i = 0; i < detailRows.size(); i++) {
            int rx = listX;
            int ry = listY + i * rowH;
            if (ry > my + modalH - 24) break;

            DetailRow row = detailRows.get(i);
            boolean hovered = mouseX >= rx && mouseX < rx + modalW - 24 && mouseY >= ry && mouseY < ry + rowH;
            
            if (hovered) {
                graphics.fill(rx, ry, rx + modalW - 24, ry + rowH - 1, 0x1Affffff);
            }

            graphics.drawString(font, row.label, rx + 4, ry + 3, 0xFFe6e6fa);

            if (row.type.equals("item")) {
                int xx = rx + modalW - 38;
                boolean xHovered = mouseX >= xx && mouseX < xx + 10 && mouseY >= ry + 2 && mouseY < ry + 12;
                graphics.drawString(font, "§c[X]", xx, ry + 3, xHovered ? 0xFFff0000 : 0xFFd93b3b);
            }
        }

        graphics.drawString(font, Component.translatable("rpg_mounts.evolution.editor.row_hint"), mx + 12, my + modalH - 15, 0xFF888888);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showUnsavedChangesModal) {
            int modalW = 200;
            int modalH = 80;
            int mx = (width - modalW) / 2;
            int my = (height - modalH) / 2;

            if (button == 0) {
                if (mouseX >= mx + 16 && mouseX < mx + 80 && mouseY >= my + 48 && mouseY < my + 68) {
                    showUnsavedChangesModal = false;
                    isDirty = false;
                    this.minecraft.setScreen(null);
                    return true;
                }
                if (mouseX >= mx + 120 && mouseX < mx + 184 && mouseY >= my + 48 && mouseY < my + 68) {
                    showUnsavedChangesModal = false;
                    return true;
                }
            }
            return true;
        }

        if (showInputDialog) {
            return true;
        }

        if (showTemplateSelector) {
            int modalW = 180;
            int modalH = 180;
            int mx = (width - modalW) / 2;
            int my = (height - modalH) / 2;
            int startY = my + 48;
            int rowH = 18;
            int maxRows = 6;

            int index = templateScrollOffset;
            for (int i = 0; i < maxRows; i++) {
                int ry = startY + i * rowH;
                if (mouseX >= mx + 12 && mouseX < mx + modalW - 12 && mouseY >= ry && mouseY < ry + rowH) {
                    if (index < filteredTemplateIds.size()) {
                        String selectedTemplate = filteredTemplateIds.get(index);
                        showTemplateSelector = false;
                        int snappedX = Math.round((float) canvasSpawnX / 8.0f) * 8;
                        int snappedY = Math.round((float) canvasSpawnY / 8.0f) * 8;
                        spawnNewBranchNode(selectedTemplate, snappedX, snappedY);
                        return true;
                    }
                }
                index++;
            }
            if (mouseX < mx || mouseX >= mx + modalW || mouseY < my || mouseY >= my + modalH) {
                showTemplateSelector = false;
            }
            return true;
        }

        if (showCatalystSelector) {
            int modalW = 200;
            int modalH = 200;
            int mx = (width - modalW) / 2;
            int my = (height - modalH) / 2;
            int gridX = mx + 12;
            int gridY = my + 50;
            int cols = 5;
            int rows = 5;
            int slotSize = 24;

            int index = catalystScrollOffset;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int sx = gridX + c * slotSize;
                    int sy = gridY + r * slotSize;
                    if (mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize) {
                        if (index < filteredCatalystIds.size()) {
                            String selectedId = filteredCatalystIds.get(index);
                            showCatalystSelector = false;
                            if (catalystCallback != null) {
                                catalystCallback.accept(selectedId);
                            }
                            return true;
                        }
                    }
                    index++;
                }
            }
            return true;
        }

        if (showDetailsPopup) {
            int modalW = 240;
            int modalH = 180;
            int mx = (width - modalW) / 2;
            int my = (height - modalH) / 2;
            int listX = mx + 12;
            int listY = my + 30;
            int rowH = 15;

            for (int i = 0; i < detailRows.size(); i++) {
                int rx = listX;
                int ry = listY + i * rowH;
                if (ry > my + modalH - 24) break;

                DetailRow row = detailRows.get(i);
                
                if (row.type.equals("item")) {
                    int xx = rx + modalW - 38;
                    if (mouseX >= xx && mouseX < xx + 10 && mouseY >= ry + 2 && mouseY < ry + 12) {
                        executeCommand(new IEditorCommand() {
                            @Override
                            public void execute() {
                                detailsTargetBranch.conditions.items.remove((EvolutionTreeManager.ItemCatalyst) row.data);
                                rebuildDetailRows();
                                isDirty = true;
                            }

                            @Override
                            public void undo() {
                                detailsTargetBranch.conditions.items.add((EvolutionTreeManager.ItemCatalyst) row.data);
                                rebuildDetailRows();
                                isDirty = true;
                            }
                        });
                        return true;
                    }
                }

                if (mouseX >= rx && mouseX < rx + modalW - 24 && mouseY >= ry && mouseY < ry + rowH) {
                    if (button == 0 || button == 1) {
                        openDetailRowContextMenu((int) mouseX, (int) mouseY, row);
                        return true;
                    }
                }
            }
            
            if (mouseX < mx || mouseX >= mx + modalW || mouseY < my || mouseY >= my + modalH) {
                showDetailsPopup = false;
            }
            return true;
        }

        if (showContextMenu) {
            int menuW = 120;
            if (mouseX >= contextMenuX && mouseX < contextMenuX + menuW && mouseY >= contextMenuY && mouseY < contextMenuY + contextMenuItems.size() * 16 + 4) {
                int index = (int) ((mouseY - contextMenuY - 2) / 16);
                if (index >= 0 && index < contextMenuItems.size()) {
                    contextMenuItems.get(index).action.run();
                }
                showContextMenu = false;
                return true;
            }
            showContextMenu = false;
        }

        // Sidebar click check
        if (mouseX < SIDEBAR_W) {
            int yOffset = 24;
            for (int i = scrollOffset; i < loadedMountTemplateKeys.size(); i++) {
                if (yOffset > height - 20) break;
                if (mouseY >= yOffset && mouseY < yOffset + 20) {
                    draggingTemplateId = loadedMountTemplateKeys.get(i);
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    isDraggingTemplate = true;
                    return true;
                }
                yOffset += 22;
            }
            return true;
        }

        // Toolbar check
        if (mouseY < 24) {
            int toolbarX = SIDEBAR_W + 8;
            if (mouseX >= toolbarX && mouseX < toolbarX + 48) {
                saveTreeToServer();
                return true;
            }
            toolbarX += 54;
            if (mouseX >= toolbarX && mouseX < toolbarX + 72) {
                executeAutoLayout();
                return true;
            }
            return true;
        }

        // Canvas local clicks
        double canvasMouseX = (mouseX - SIDEBAR_W - (width - SIDEBAR_W) / 2.0 - panX) / zoom;
        double canvasMouseY = (mouseY - (height) / 2.0 - panY) / zoom;

        long now = System.currentTimeMillis();
        boolean isDoubleClick = (now - lastClickTime < 250) && (Math.abs(mouseX - lastClickX) < 4) && (Math.abs(mouseY - lastClickY) < 4);
        lastClickTime = now;
        lastClickX = mouseX;
        lastClickY = mouseY;

        // Check clicks on reroute points
        if (activeTree != null) {
            for (EvolutionTreeManager.EvolutionBranch branch : activeTree.branches) {
                for (int i = 0; i < branch.reroutePoints.size(); i++) {
                    EvolutionTreeManager.Vec2i pt = branch.reroutePoints.get(i);
                    if (canvasMouseX >= pt.x - 4 && canvasMouseX <= pt.x + 4 && canvasMouseY >= pt.y - 4 && canvasMouseY <= pt.y + 4) {
                        if (button == 0) {
                            activeDraggingRerouteBranch = branch;
                            activeDraggingRerouteIndex = i;
                            dragNodeOffsetX = (int) (pt.x - canvasMouseX);
                            dragNodeOffsetY = (int) (pt.y - canvasMouseY);
                            dragRerouteStartCoordsX = pt.x;
                            dragRerouteStartCoordsY = pt.y;
                            return true;
                        } else if (button == 1) {
                            openRerouteContextMenu((int) mouseX, (int) mouseY, branch, i);
                            return true;
                        }
                    }
                }
            }
        }

        boolean hitNode = false;
        if (activeTree != null) {
            for (EvolutionTreeManager.EvolutionBranch branch : activeTree.branches) {
                int x = branch.xCoord - CARD_W / 2;
                int y = branch.yCoord - CARD_H / 2;
                if (canvasMouseX >= x && canvasMouseX < x + CARD_W && canvasMouseY >= y && canvasMouseY < y + CARD_H) {
                    hitNode = true;
                    if (button == 0) {
                        if (hasControlDown()) {
                            linkParentCandidate = branch;
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.sendSystemMessage(Component.translatable("gui.rpg_mounts.evolution.editor.selected_parent", branch.displayName));
                            }
                        } else if (isDoubleClick) {
                            openDetailsPopup(branch);
                        } else {
                            if (hasShiftDown()) {
                                if (selectedNodes.contains(branch)) {
                                    selectedNodes.remove(branch);
                                } else {
                                    selectedNodes.add(branch);
                                }
                            } else {
                                if (!selectedNodes.contains(branch)) {
                                    selectedNodes.clear();
                                    selectedNodes.add(branch);
                                }
                                activeDraggingNode = branch;
                                dragNodeOffsetX = (int) (branch.xCoord - canvasMouseX);
                                dragNodeOffsetY = (int) (branch.yCoord - canvasMouseY);
                                
                                dragStartCoords.clear();
                                for (EvolutionTreeManager.EvolutionBranch n : selectedNodes) {
                                    dragStartCoords.put(n.targetId, new Vec2iOld(n.xCoord, n.yCoord));
                                }
                            }
                        }
                    } else if (button == 1) {
                        openNodeContextMenu((int) mouseX, (int) mouseY, branch);
                    }
                    break;
                }
            }
        }

        if (!hitNode && button == 0) {
            if (isDoubleClick) {
                // Check if spline is clicked to add reroute point
                for (EvolutionTreeManager.EvolutionBranch branch : activeTree.branches) {
                    int segmentIdx = findClickedSplineSegmentIndex(branch, canvasMouseX, canvasMouseY, zoom);
                    if (segmentIdx >= 0) {
                        int rx = Math.round((float) canvasMouseX / 8.0f) * 8;
                        int ry = Math.round((float) canvasMouseY / 8.0f) * 8;
                        splitSplineAndAddReroute(branch, segmentIdx, rx, ry);
                        return true;
                    }
                }
            } else if (hasShiftDown()) {
                isBoxSelecting = true;
                boxSelectStartX = mouseX;
                boxSelectStartY = mouseY;
                boxSelectEndX = mouseX;
                boxSelectEndY = mouseY;
            } else {
                selectedNodes.clear();
                isDraggingCanvas = true;
                dragStartX = mouseX - panX;
                dragStartY = mouseY - panY;
            }
        }
        if (!hitNode && button == 1) {
            openCanvasContextMenu((int) mouseX, (int) mouseY);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingTemplate) {
            isDraggingTemplate = false;
            double dist = Math.sqrt((mouseX - dragStartX) * (mouseX - dragStartX) + (mouseY - dragStartY) * (mouseY - dragStartY));
            if (dist < 4) {
                loadTree(draggingTemplateId);
            } else if (mouseX >= SIDEBAR_W && activeTree != null) {
                double canvasMouseX = (mouseX - SIDEBAR_W - (width - SIDEBAR_W) / 2.0 - panX) / zoom;
                double canvasMouseY = (mouseY - (height) / 2.0 - panY) / zoom;
                int snappedX = Math.round((float) canvasMouseX / 8.0f) * 8;
                int snappedY = Math.round((float) canvasMouseY / 8.0f) * 8;
                spawnNewBranchNode(draggingTemplateId, snappedX, snappedY);
            }
            draggingTemplateId = null;
            return true;
        }
        if (isBoxSelecting) {
            isBoxSelecting = false;
            evaluateBoxSelection();
        }
        if (isDraggingCanvas) {
            isDraggingCanvas = false;
        }
        if (activeDraggingNode != null) {
            applyOverlapCheckAndRepulsion(activeDraggingNode);
            recordMoveCommand();
            activeDraggingNode = null;
        }
        if (activeDraggingRerouteBranch != null) {
            EvolutionTreeManager.Vec2i pt = activeDraggingRerouteBranch.reroutePoints.get(activeDraggingRerouteIndex);
            recordRerouteMoveCommand(activeDraggingRerouteBranch, activeDraggingRerouteIndex, dragRerouteStartCoordsX, dragRerouteStartCoordsY, pt.x, pt.y);
            activeDraggingRerouteBranch = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingCanvas) {
            panX = mouseX - dragStartX;
            panY = mouseY - dragStartY;
            return true;
        }
        if (isBoxSelecting) {
            boxSelectEndX = mouseX;
            boxSelectEndY = mouseY;
            return true;
        }
        if (activeDraggingNode != null) {
            double canvasMouseX = (mouseX - SIDEBAR_W - (width - SIDEBAR_W) / 2.0 - panX) / zoom;
            double canvasMouseY = (mouseY - (height) / 2.0 - panY) / zoom;

            int rawX = (int) (canvasMouseX + dragNodeOffsetX);
            int rawY = (int) (canvasMouseY + dragNodeOffsetY);
            
            int snappedX = Math.round(rawX / 8.0f) * 8;
            int snappedY = Math.round(rawY / 8.0f) * 8;

            int diffX = snappedX - activeDraggingNode.xCoord;
            int diffY = snappedY - activeDraggingNode.yCoord;

            for (EvolutionTreeManager.EvolutionBranch node : selectedNodes) {
                node.xCoord += diffX;
                node.yCoord += diffY;
            }
            isDirty = true;
            return true;
        }
        if (activeDraggingRerouteBranch != null) {
            double canvasMouseX = (mouseX - SIDEBAR_W - (width - SIDEBAR_W) / 2.0 - panX) / zoom;
            double canvasMouseY = (mouseY - (height) / 2.0 - panY) / zoom;

            int rawX = (int) (canvasMouseX + dragNodeOffsetX);
            int rawY = (int) (canvasMouseY + dragNodeOffsetY);
            
            int snappedX = Math.round(rawX / 8.0f) * 8;
            int snappedY = Math.round(rawY / 8.0f) * 8;

            EvolutionTreeManager.Vec2i pt = activeDraggingRerouteBranch.reroutePoints.get(activeDraggingRerouteIndex);
            pt.x = snappedX;
            pt.y = snappedY;
            isDirty = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= SIDEBAR_W) {
            double oldZoom = zoom;
            zoom = Math.max(0.5, Math.min(2.0, zoom + amount * 0.1));
            
            double mouseCanvasX = (mouseX - SIDEBAR_W - (width - SIDEBAR_W) / 2.0 - panX) / oldZoom;
            double mouseCanvasY = (mouseY - (height) / 2.0 - panY) / oldZoom;
            
            panX = mouseX - SIDEBAR_W - (width - SIDEBAR_W) / 2.0 - mouseCanvasX * zoom;
            panY = mouseY - (height / 2.0) - mouseCanvasY * zoom;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private void openCanvasContextMenu(int mouseX, int mouseY) {
        showContextMenu = true;
        contextMenuX = mouseX;
        contextMenuY = mouseY;
        contextMenuTargetNode = null;
        contextMenuItems.clear();

        double canvasMouseX = (mouseX - SIDEBAR_W - (width - SIDEBAR_W) / 2.0 - panX) / zoom;
        double canvasMouseY = (mouseY - (height) / 2.0 - panY) / zoom;

        contextMenuItems.add(new ContextMenuItem("Add Node", () -> {
            openTemplateSelector(canvasMouseX, canvasMouseY);
        }));

        contextMenuItems.add(new ContextMenuItem("Undo", this::undo));
        contextMenuItems.add(new ContextMenuItem("Redo", this::redo));
        contextMenuItems.add(new ContextMenuItem("Auto-Layout", this::executeAutoLayout));
        contextMenuItems.add(new ContextMenuItem("Save Configuration", this::saveTreeToServer));
    }

    private void openNodeContextMenu(int mouseX, int mouseY, EvolutionTreeManager.EvolutionBranch node) {
        showContextMenu = true;
        contextMenuX = mouseX;
        contextMenuY = mouseY;
        contextMenuTargetNode = node;
        contextMenuItems.clear();

        contextMenuItems.add(new ContextMenuItem("Edit Display Name", () -> {
            openInputDialog("Edit Display Name", node.displayName, val -> {
                String oldJson = new Gson().toJson(node);
                node.displayName = val;
                String newJson = new Gson().toJson(node);
                executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
            });
        }));

        if (linkParentCandidate != null && linkParentCandidate != node) {
            contextMenuItems.add(new ContextMenuItem("Link: Set as Parent", () -> {
                executeCommand(new CreateLinkCommand(this, node, linkParentCandidate.targetId));
                linkParentCandidate = null;
            }));
        } else {
            contextMenuItems.add(new ContextMenuItem("Link: Clear Parent", () -> {
                executeCommand(new CreateLinkCommand(this, node, ""));
            }));
        }

        contextMenuItems.add(new ContextMenuItem("Add Req: Level", () -> {
            openInputDialog("Enter Required Level", "10", val -> {
                try {
                    int lvl = Integer.parseInt(val);
                    String oldJson = new Gson().toJson(node);
                    node.requiredLevel = lvl;
                    String newJson = new Gson().toJson(node);
                    executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
                } catch (NumberFormatException ignored) {}
            });
        }));

        contextMenuItems.add(new ContextMenuItem("Add Req: Bonding", () -> {
            openInputDialog("Enter Required Bonding", "50", val -> {
                try {
                    int bnd = Integer.parseInt(val);
                    String oldJson = new Gson().toJson(node);
                    node.requiredBonding = bnd;
                    String newJson = new Gson().toJson(node);
                    executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
                } catch (NumberFormatException ignored) {}
            });
        }));

        contextMenuItems.add(new ContextMenuItem("Add Req: Dimension", () -> {
            openInputDialog("Enter Dimension ID", "minecraft:the_nether", val -> {
                String oldJson = new Gson().toJson(node);
                node.conditions.dimension = val;
                String newJson = new Gson().toJson(node);
                executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
            });
        }));

        contextMenuItems.add(new ContextMenuItem("Add Req: Biome", () -> {
            openInputDialog("Enter Biome ID", "minecraft:soul_sand_valley", val -> {
                String oldJson = new Gson().toJson(node);
                node.conditions.biome = val;
                String newJson = new Gson().toJson(node);
                executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
            });
        }));

        contextMenuItems.add(new ContextMenuItem("Add Req: Chroma Parent", () -> {
            String oldJson = new Gson().toJson(node);
            node.conditions.requiresChroma = true;
            String newJson = new Gson().toJson(node);
            executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
        }));

        contextMenuItems.add(new ContextMenuItem("Add Req: Status Effect", () -> {
            openInputDialog("Enter Effect ID", "minecraft:fire_resistance", effectId -> {
                openInputDialog("Enter Minimum Amplifier", "0", ampVal -> {
                    try {
                        int amp = Integer.parseInt(ampVal);
                        String oldJson = new Gson().toJson(node);
                        node.conditions.statusEffects.add(new EvolutionTreeManager.StatusEffectConfig(effectId, amp));
                        String newJson = new Gson().toJson(node);
                        executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
                    } catch (NumberFormatException ignored) {}
                });
            });
        }));

        contextMenuItems.add(new ContextMenuItem("Add Req: Mob Kill", () -> {
            openInputDialog("Enter Entity Type ID", "minecraft:wither", mobId -> {
                openInputDialog("Enter Kill Count", "1", countVal -> {
                    try {
                        int count = Integer.parseInt(countVal);
                        String oldJson = new Gson().toJson(node);
                        node.conditions.mobKills.add(new EvolutionTreeManager.MobKillConfig(mobId, count));
                        String newJson = new Gson().toJson(node);
                        executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
                    } catch (NumberFormatException ignored) {}
                });
            });
        }));

        contextMenuItems.add(new ContextMenuItem("Add Req: Weather", () -> {
            openInputDialog("Enter Weather State (clear/rain/thunder)", "clear", val -> {
                String oldJson = new Gson().toJson(node);
                node.conditions.weather = val;
                String newJson = new Gson().toJson(node);
                executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
            });
        }));

        contextMenuItems.add(new ContextMenuItem("Add Req: Moon Phase", () -> {
            openInputDialog("Enter Moon Phase Index (0-7)", "0", val -> {
                try {
                    int phase = Integer.parseInt(val);
                    String oldJson = new Gson().toJson(node);
                    if (!node.conditions.moonPhases.contains(phase)) {
                        node.conditions.moonPhases.add(phase);
                    }
                    String newJson = new Gson().toJson(node);
                    executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
                } catch (NumberFormatException ignored) {}
            });
        }));

        contextMenuItems.add(new ContextMenuItem("Add Req: Damage Dealt", () -> {
            openInputDialog("Enter Required Damage Dealt", "1000", val -> {
                try {
                    double amt = Double.parseDouble(val);
                    String oldJson = new Gson().toJson(node);
                    node.conditions.damageDealt = amt;
                    String newJson = new Gson().toJson(node);
                    executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
                } catch (NumberFormatException ignored) {}
            });
        }));

        contextMenuItems.add(new ContextMenuItem("Add Req: Damage Taken", () -> {
            openInputDialog("Enter Required Damage Taken", "1000", val -> {
                try {
                    double amt = Double.parseDouble(val);
                    String oldJson = new Gson().toJson(node);
                    node.conditions.damageTaken = amt;
                    String newJson = new Gson().toJson(node);
                    executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
                } catch (NumberFormatException ignored) {}
            });
        }));

        contextMenuItems.add(new ContextMenuItem("Add Req: HP Zero Count", () -> {
            openInputDialog("Enter Required HP Zero Count", "5", val -> {
                try {
                    int count = Integer.parseInt(val);
                    String oldJson = new Gson().toJson(node);
                    node.conditions.hpZeroCount = count;
                    String newJson = new Gson().toJson(node);
                    executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
                } catch (NumberFormatException ignored) {}
            });
        }));

        contextMenuItems.add(new ContextMenuItem("Add Req: Distance Travelled", () -> {
            openInputDialog("Enter Required Distance Travelled", "5000", val -> {
                try {
                    double dist = Double.parseDouble(val);
                    String oldJson = new Gson().toJson(node);
                    node.conditions.distanceTravelled = dist;
                    String newJson = new Gson().toJson(node);
                    executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
                } catch (NumberFormatException ignored) {}
            });
        }));

        contextMenuItems.add(new ContextMenuItem("Add Catalyst", () -> {
            openCatalystSelector(selectedItemId -> {
                openInputDialog("Enter Catalyst Count", "1", val -> {
                    try {
                        int count = Integer.parseInt(val);
                        String oldJson = new Gson().toJson(node);
                        node.conditions.items.add(new EvolutionTreeManager.ItemCatalyst(selectedItemId, count));
                        String newJson = new Gson().toJson(node);
                        executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
                    } catch (NumberFormatException ignored) {}
                });
            });
        }));

        contextMenuItems.add(new ContextMenuItem("Configure Exclusions", () -> {
            openInputDialog("Enter Excluded Mount ID", "rpg_mounts:fenrir", val -> {
                String oldJson = new Gson().toJson(node);
                if (!node.excludes.contains(val)) {
                    node.excludes.add(val);
                }
                String newJson = new Gson().toJson(node);
                executeCommand(new UpdatePropertiesCommand(this, node, oldJson, newJson));
            });
        }));

        contextMenuItems.add(new ContextMenuItem("Delete Node", () -> executeNodeDeletion(node)));
    }

    private void openRerouteContextMenu(int mouseX, int mouseY, EvolutionTreeManager.EvolutionBranch branch, int index) {
        showContextMenu = true;
        contextMenuX = mouseX;
        contextMenuY = mouseY;
        contextMenuItems.clear();

        contextMenuItems.add(new ContextMenuItem("Delete Reroute Node", () -> {
            EvolutionTreeManager.Vec2i pt = branch.reroutePoints.get(index);
            executeCommand(new IEditorCommand() {
                @Override
                public void execute() {
                    branch.reroutePoints.remove(pt);
                    isDirty = true;
                }

                @Override
                public void undo() {
                    branch.reroutePoints.add(index, pt);
                    isDirty = true;
                }
            });
        }));
    }

    private void openDetailsPopup(EvolutionTreeManager.EvolutionBranch branch) {
        showDetailsPopup = true;
        detailsTargetBranch = branch;
        rebuildDetailRows();
    }

    private void rebuildDetailRows() {
        detailRows.clear();
        if (detailsTargetBranch == null) return;
        
        detailRows.add(new DetailRow("Required Level: " + detailsTargetBranch.requiredLevel, "level", detailsTargetBranch));
        detailRows.add(new DetailRow("Required Bonding: " + detailsTargetBranch.requiredBonding, "bonding", detailsTargetBranch));
        
        EvolutionTreeManager.PrerequisiteConditions conds = detailsTargetBranch.conditions;
        if (conds != null) {
            detailRows.add(new DetailRow("Requires Chroma: " + conds.requiresChroma, "chroma", conds));
            if (conds.dimension != null && !conds.dimension.isEmpty()) {
                detailRows.add(new DetailRow("Dimension: " + conds.dimension, "dimension", conds));
            }
            if (conds.biome != null && !conds.biome.isEmpty()) {
                detailRows.add(new DetailRow("Biome: " + conds.biome, "biome", conds));
            }
            if (conds.weather != null && !conds.weather.isEmpty()) {
                detailRows.add(new DetailRow("Weather: " + conds.weather, "weather", conds));
            }
            if (conds.damageDealt > 0) {
                detailRows.add(new DetailRow("Damage Dealt: " + conds.damageDealt, "damage_dealt", conds));
            }
            if (conds.damageTaken > 0) {
                detailRows.add(new DetailRow("Damage Taken: " + conds.damageTaken, "damage_taken", conds));
            }
            if (conds.hpZeroCount > 0) {
                detailRows.add(new DetailRow("HP Zero Recoveries: " + conds.hpZeroCount, "hp_zero", conds));
            }
            if (conds.distanceTravelled > 0) {
                detailRows.add(new DetailRow("Distance Travelled: " + conds.distanceTravelled, "distance", conds));
            }
            
            for (EvolutionTreeManager.ItemCatalyst item : conds.items) {
                detailRows.add(new DetailRow("Catalyst: " + item.id.replace("minecraft:", "") + " x" + item.count, "item", item));
            }
            for (EvolutionTreeManager.StatusEffectConfig eff : conds.statusEffects) {
                detailRows.add(new DetailRow("Effect: " + eff.id.replace("minecraft:", "") + " Amp " + eff.amplifier, "status_effect", eff));
            }
            for (EvolutionTreeManager.MobKillConfig kill : conds.mobKills) {
                detailRows.add(new DetailRow("Kill: " + kill.id.replace("minecraft:", "") + " x" + kill.count, "mob_kill", kill));
            }
            if (!conds.moonPhases.isEmpty()) {
                detailRows.add(new DetailRow("Moon Phases: " + conds.moonPhases.toString(), "moon_phase", conds));
            }
        }
    }

    private void openDetailRowContextMenu(int mouseX, int mouseY, DetailRow row) {
        showContextMenu = true;
        contextMenuX = mouseX;
        contextMenuY = mouseY;
        contextMenuItems.clear();

        contextMenuItems.add(new ContextMenuItem("Edit Value", () -> editDetailRowValue(row)));
        contextMenuItems.add(new ContextMenuItem("Remove", () -> removeDetailRow(row)));
    }

    private void editDetailRowValue(DetailRow row) {
        if (row.type.equals("level")) {
            openInputDialog("Edit Required Level", String.valueOf(detailsTargetBranch.requiredLevel), val -> {
                try {
                    int num = Integer.parseInt(val);
                    String oldJson = new Gson().toJson(detailsTargetBranch);
                    detailsTargetBranch.requiredLevel = num;
                    String newJson = new Gson().toJson(detailsTargetBranch);
                    executeCommand(new UpdatePropertiesCommand(this, detailsTargetBranch, oldJson, newJson));
                    rebuildDetailRows();
                } catch (NumberFormatException ignored) {}
            });
        } else if (row.type.equals("bonding")) {
            openInputDialog("Edit Required Bonding", String.valueOf(detailsTargetBranch.requiredBonding), val -> {
                try {
                    int num = Integer.parseInt(val);
                    String oldJson = new Gson().toJson(detailsTargetBranch);
                    detailsTargetBranch.requiredBonding = num;
                    String newJson = new Gson().toJson(detailsTargetBranch);
                    executeCommand(new UpdatePropertiesCommand(this, detailsTargetBranch, oldJson, newJson));
                    rebuildDetailRows();
                } catch (NumberFormatException ignored) {}
            });
        } else if (row.type.equals("chroma")) {
            String oldJson = new Gson().toJson(detailsTargetBranch);
            detailsTargetBranch.conditions.requiresChroma = !detailsTargetBranch.conditions.requiresChroma;
            String newJson = new Gson().toJson(detailsTargetBranch);
            executeCommand(new UpdatePropertiesCommand(this, detailsTargetBranch, oldJson, newJson));
            rebuildDetailRows();
        } else if (row.type.equals("item")) {
            EvolutionTreeManager.ItemCatalyst catalyst = (EvolutionTreeManager.ItemCatalyst) row.data;
            openInputDialog("Edit Catalyst Count", String.valueOf(catalyst.count), val -> {
                try {
                    int num = Integer.parseInt(val);
                    String oldJson = new Gson().toJson(detailsTargetBranch);
                    catalyst.count = num;
                    String newJson = new Gson().toJson(detailsTargetBranch);
                    executeCommand(new UpdatePropertiesCommand(this, detailsTargetBranch, oldJson, newJson));
                    rebuildDetailRows();
                } catch (NumberFormatException ignored) {}
            });
        } else if (row.type.equals("dimension")) {
            openInputDialog("Edit Dimension ID", detailsTargetBranch.conditions.dimension, val -> {
                String oldJson = new Gson().toJson(detailsTargetBranch);
                detailsTargetBranch.conditions.dimension = val;
                String newJson = new Gson().toJson(detailsTargetBranch);
                executeCommand(new UpdatePropertiesCommand(this, detailsTargetBranch, oldJson, newJson));
                rebuildDetailRows();
            });
        } else if (row.type.equals("biome")) {
            openInputDialog("Edit Biome ID", detailsTargetBranch.conditions.biome, val -> {
                String oldJson = new Gson().toJson(detailsTargetBranch);
                detailsTargetBranch.conditions.biome = val;
                String newJson = new Gson().toJson(detailsTargetBranch);
                executeCommand(new UpdatePropertiesCommand(this, detailsTargetBranch, oldJson, newJson));
                rebuildDetailRows();
            });
        } else if (row.type.equals("weather")) {
            openInputDialog("Edit Weather (clear/rain/thunder)", detailsTargetBranch.conditions.weather, val -> {
                String oldJson = new Gson().toJson(detailsTargetBranch);
                detailsTargetBranch.conditions.weather = val;
                String newJson = new Gson().toJson(detailsTargetBranch);
                executeCommand(new UpdatePropertiesCommand(this, detailsTargetBranch, oldJson, newJson));
                rebuildDetailRows();
            });
        } else if (row.type.equals("status_effect")) {
            EvolutionTreeManager.StatusEffectConfig cfg = (EvolutionTreeManager.StatusEffectConfig) row.data;
            openInputDialog("Edit Effect Amplifier", String.valueOf(cfg.amplifier), val -> {
                try {
                    int num = Integer.parseInt(val);
                    String oldJson = new Gson().toJson(detailsTargetBranch);
                    cfg.amplifier = num;
                    String newJson = new Gson().toJson(detailsTargetBranch);
                    executeCommand(new UpdatePropertiesCommand(this, detailsTargetBranch, oldJson, newJson));
                    rebuildDetailRows();
                } catch (NumberFormatException ignored) {}
            });
        } else if (row.type.equals("mob_kill")) {
            EvolutionTreeManager.MobKillConfig cfg = (EvolutionTreeManager.MobKillConfig) row.data;
            openInputDialog("Edit Kill Count", String.valueOf(cfg.count), val -> {
                try {
                    int num = Integer.parseInt(val);
                    String oldJson = new Gson().toJson(detailsTargetBranch);
                    cfg.count = num;
                    String newJson = new Gson().toJson(detailsTargetBranch);
                    executeCommand(new UpdatePropertiesCommand(this, detailsTargetBranch, oldJson, newJson));
                    rebuildDetailRows();
                } catch (NumberFormatException ignored) {}
            });
        }
    }

    private void removeDetailRow(DetailRow row) {
        String oldJson = new Gson().toJson(detailsTargetBranch);
        if (row.type.equals("level")) {
            detailsTargetBranch.requiredLevel = 0;
        } else if (row.type.equals("bonding")) {
            detailsTargetBranch.requiredBonding = 0;
        } else if (row.type.equals("chroma")) {
            detailsTargetBranch.conditions.requiresChroma = false;
        } else if (row.type.equals("dimension")) {
            detailsTargetBranch.conditions.dimension = "";
        } else if (row.type.equals("biome")) {
            detailsTargetBranch.conditions.biome = "";
        } else if (row.type.equals("weather")) {
            detailsTargetBranch.conditions.weather = "";
        } else if (row.type.equals("item")) {
            detailsTargetBranch.conditions.items.remove((EvolutionTreeManager.ItemCatalyst) row.data);
        } else if (row.type.equals("status_effect")) {
            detailsTargetBranch.conditions.statusEffects.remove((EvolutionTreeManager.StatusEffectConfig) row.data);
        } else if (row.type.equals("mob_kill")) {
            detailsTargetBranch.conditions.mobKills.remove((EvolutionTreeManager.MobKillConfig) row.data);
        }
        String newJson = new Gson().toJson(detailsTargetBranch);
        executeCommand(new UpdatePropertiesCommand(this, detailsTargetBranch, oldJson, newJson));
        rebuildDetailRows();
    }

    private void spawnNewBranchNode(String templateId, int x, int y) {
        if (activeTree == null) return;
        
        for (EvolutionTreeManager.EvolutionBranch b : activeTree.branches) {
            if (b.targetId.equals(templateId)) {
                return;
            }
        }
        
        EvolutionTreeManager.EvolutionBranch branch = new EvolutionTreeManager.EvolutionBranch();
        branch.targetId = templateId;
        MountData template = MountRegistry.getTemplate(templateId);
        branch.displayName = template != null ? template.name : templateId;
        branch.xCoord = x;
        branch.yCoord = y;
        
        executeCommand(new IEditorCommand() {
            @Override
            public void execute() {
                activeTree.branches.add(branch);
                applyOverlapCheckAndRepulsion(branch);
                isDirty = true;
            }

            @Override
            public void undo() {
                activeTree.branches.remove(branch);
                isDirty = true;
            }
        });
    }

    private void splitSplineAndAddReroute(EvolutionTreeManager.EvolutionBranch branch, int index, int rx, int ry) {
        EvolutionTreeManager.Vec2i newPt = new EvolutionTreeManager.Vec2i(rx, ry);
        executeCommand(new IEditorCommand() {
            @Override
            public void execute() {
                branch.reroutePoints.add(index, newPt);
                isDirty = true;
            }

            @Override
            public void undo() {
                branch.reroutePoints.remove(newPt);
                isDirty = true;
            }
        });
    }

    private void executeNodeDeletion(EvolutionTreeManager.EvolutionBranch node) {
        if (activeTree == null) return;
        
        EvolutionTreeManager.EvolutionBranch deletedNode = node;
        int index = activeTree.branches.indexOf(node);
        
        executeCommand(new IEditorCommand() {
            @Override
            public void execute() {
                activeTree.branches.remove(deletedNode);
                isDirty = true;
            }

            @Override
            public void undo() {
                activeTree.branches.add(index, deletedNode);
                isDirty = true;
            }
        });
    }

    private void executeAutoLayout() {
        if (activeTree == null || activeTree.branches.isEmpty()) return;

        double angleStep = Math.PI * 2 / activeTree.branches.size();
        int radius = 100;
        
        Map<String, Vec2iOld> oldCoords = new HashMap<>();
        for (EvolutionTreeManager.EvolutionBranch branch : activeTree.branches) {
            oldCoords.put(branch.targetId, new Vec2iOld(branch.xCoord, branch.yCoord));
        }

        executeCommand(new IEditorCommand() {
            @Override
            public void execute() {
                for (int i = 0; i < activeTree.branches.size(); i++) {
                    EvolutionTreeManager.EvolutionBranch branch = activeTree.branches.get(i);
                    double angle = angleStep * i;
                    branch.xCoord = (int) (Math.cos(angle) * radius);
                    branch.yCoord = (int) (Math.sin(angle) * radius);
                }
                isDirty = true;
            }

            @Override
            public void undo() {
                for (EvolutionTreeManager.EvolutionBranch branch : activeTree.branches) {
                    Vec2iOld coords = oldCoords.get(branch.targetId);
                    if (coords != null) {
                        branch.xCoord = coords.x;
                        branch.yCoord = coords.y;
                    }
                }
                isDirty = true;
            }
        });
    }

    private void applyOverlapCheckAndRepulsion(EvolutionTreeManager.EvolutionBranch node) {
        if (activeTree == null) return;
        boolean overlapFound = true;
        int depth = 0;
        
        while (overlapFound && depth < 10) {
            overlapFound = false;
            for (EvolutionTreeManager.EvolutionBranch b : activeTree.branches) {
                if (b == node) continue;
                
                int dx = Math.abs(node.xCoord - b.xCoord);
                int dy = Math.abs(node.yCoord - b.yCoord);
                
                if (dx < CARD_W && dy < CARD_H) {
                    overlapFound = true;
                    int overlapX = CARD_W - dx;
                    int overlapY = CARD_H - dy;
                    if (overlapX < overlapY) {
                        node.xCoord += (node.xCoord >= b.xCoord) ? overlapX : -overlapX;
                    } else {
                        node.yCoord += (node.yCoord >= b.yCoord) ? overlapY : -overlapY;
                    }
                    node.xCoord = Math.round(node.xCoord / 8.0f) * 8;
                    node.yCoord = Math.round(node.yCoord / 8.0f) * 8;
                }
            }
            depth++;
        }
    }

    private void evaluateBoxSelection() {
        selectedNodes.clear();
        if (activeTree == null) return;

        double minX = (Math.min(boxSelectStartX, boxSelectEndX) - SIDEBAR_W - (width - SIDEBAR_W) / 2.0 - panX) / zoom;
        double maxX = (Math.max(boxSelectStartX, boxSelectEndX) - SIDEBAR_W - (width - SIDEBAR_W) / 2.0 - panX) / zoom;
        double minY = (Math.min(boxSelectStartY, boxSelectEndY) - height / 2.0 - panY) / zoom;
        double maxY = (Math.max(boxSelectStartY, boxSelectEndY) - height / 2.0 - panY) / zoom;

        for (EvolutionTreeManager.EvolutionBranch branch : activeTree.branches) {
            if (branch.xCoord >= minX && branch.xCoord <= maxX && branch.yCoord >= minY && branch.yCoord <= maxY) {
                selectedNodes.add(branch);
            }
        }
    }

    private void recordMoveCommand() {
        if (dragStartCoords.isEmpty()) return;
        
        Map<String, Vec2iOld> priors = new HashMap<>(dragStartCoords);
        Map<String, Vec2iOld> posts = new HashMap<>();
        for (EvolutionTreeManager.EvolutionBranch n : selectedNodes) {
            posts.put(n.targetId, new Vec2iOld(n.xCoord, n.yCoord));
        }
        
        boolean moved = false;
        for (Map.Entry<String, Vec2iOld> entry : priors.entrySet()) {
            Vec2iOld post = posts.get(entry.getKey());
            if (post != null && (entry.getValue().x != post.x || entry.getValue().y != post.y)) {
                moved = true;
                break;
            }
        }
        if (!moved) return;

        executeCommand(new IEditorCommand() {
            @Override
            public void execute() {
                for (EvolutionTreeManager.EvolutionBranch n : activeTree.branches) {
                    Vec2iOld post = posts.get(n.targetId);
                    if (post != null) {
                        n.xCoord = post.x;
                        n.yCoord = post.y;
                    }
                }
                isDirty = true;
            }

            @Override
            public void undo() {
                for (EvolutionTreeManager.EvolutionBranch n : activeTree.branches) {
                    Vec2iOld prior = priors.get(n.targetId);
                    if (prior != null) {
                        n.xCoord = prior.x;
                        n.yCoord = prior.y;
                    }
                }
                isDirty = true;
            }
        });
    }

    private void recordRerouteMoveCommand(EvolutionTreeManager.EvolutionBranch branch, int index, int startX, int startY, int endX, int endY) {
        if (startX == endX && startY == endY) return;
        executeCommand(new IEditorCommand() {
            @Override
            public void execute() {
                if (index < branch.reroutePoints.size()) {
                    branch.reroutePoints.get(index).x = endX;
                    branch.reroutePoints.get(index).y = endY;
                }
                isDirty = true;
            }

            @Override
            public void undo() {
                if (index < branch.reroutePoints.size()) {
                    branch.reroutePoints.get(index).x = startX;
                    branch.reroutePoints.get(index).y = startY;
                }
                isDirty = true;
            }
        });
    }

    private void executeCommand(IEditorCommand cmd) {
        cmd.execute();
        undoStack.push(cmd);
        if (undoStack.size() > MAX_HISTORY) undoStack.remove(0);
        redoStack.clear();
        isDirty = true;
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            IEditorCommand cmd = undoStack.pop();
            cmd.undo();
            redoStack.push(cmd);
            isDirty = true;
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            IEditorCommand cmd = redoStack.pop();
            cmd.execute();
            undoStack.push(cmd);
            isDirty = true;
        }
    }

    private int findClickedSplineSegmentIndex(EvolutionTreeManager.EvolutionBranch branch, double cx, double cy, double zoomVal) {
        int sx = 0, sy = 0;
        if (branch.parentId != null && !branch.parentId.isEmpty()) {
            for (EvolutionTreeManager.EvolutionBranch other : activeTree.branches) {
                if (other.targetId.equals(branch.parentId)) {
                    sx = other.xCoord;
                    sy = other.yCoord;
                    break;
                }
            }
        }
        
        List<EvolutionTreeManager.Vec2i> pts = new ArrayList<>();
        pts.add(new EvolutionTreeManager.Vec2i(sx, sy));
        pts.addAll(branch.reroutePoints);
        pts.add(new EvolutionTreeManager.Vec2i(branch.xCoord, branch.yCoord));
        
        for (int i = 0; i < pts.size() - 1; i++) {
            EvolutionTreeManager.Vec2i p0 = pts.get(i);
            EvolutionTreeManager.Vec2i p1 = pts.get(i + 1);
            
            double dx = Math.abs(p1.x - p0.x);
            double w = Math.max(32, Math.min(128, dx / 2.0));
            double p0x = p0.x;
            double p0y = p0.y;
            double p1x = p0.x + w;
            double p1y = p0.y;
            double p2x = p1.x - w;
            double p2y = p1.y;
            double p3x = p1.x;
            double p3y = p1.y;

            int segments = (int) Math.max(12, Math.min(64, 32 * zoomVal));
            double prevX = p0x;
            double prevY = p0y;

            for (int j = 1; j <= segments; j++) {
                double t = (double) j / segments;
                double t1 = 1.0 - t;
                double x = t1 * t1 * t1 * p0x + 3 * t1 * t1 * t * p1x + 3 * t1 * t * t * p2x + t * t * t * p3x;
                double y = t1 * t1 * t1 * p0y + 3 * t1 * t1 * t * p1y + 3 * t1 * t * t * p2y + t * t * t * p3y;
                
                double dist = getDistanceToSegment(cx, cy, prevX, prevY, x, y);
                if (dist < 5.0) {
                    return i;
                }
                prevX = x;
                prevY = y;
            }
        }
        return -1;
    }

    private int findClickedSplineSegmentIndex(EvolutionTreeManager.EvolutionBranch branch, double cx, double cy) {
        return findClickedSplineSegmentIndex(branch, cx, cy, zoom);
    }

    private double getDistanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            return Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0.0, Math.min(1.0, t));
        double closestX = x1 + t * dx;
        double closestY = y1 + t * dy;
        return Math.sqrt((px - closestX) * (px - closestX) + (py - closestY) * (py - closestY));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showInputDialog) {
            if (keyCode == 259) { // Backspace
                if (!inputDialogValue.isEmpty()) {
                    inputDialogValue = inputDialogValue.substring(0, inputDialogValue.length() - 1);
                }
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter
                showInputDialog = false;
                if (inputCallback != null) {
                    inputCallback.accept(inputDialogValue);
                }
                return true;
            }
            if (keyCode == 256) { // Escape
                showInputDialog = false;
                return true;
            }
            return true;
        }

        if (showTemplateSelector) {
            if (keyCode == 259) { // Backspace
                if (!templateSearchQuery.isEmpty()) {
                    templateSearchQuery = templateSearchQuery.substring(0, templateSearchQuery.length() - 1);
                    updateTemplateFilter();
                }
                return true;
            }
            if (keyCode == 265) { // Up Arrow
                templateScrollOffset = Math.max(0, templateScrollOffset - 1);
                return true;
            }
            if (keyCode == 264) { // Down Arrow
                templateScrollOffset = Math.min(Math.max(0, filteredTemplateIds.size() - 6), templateScrollOffset + 1);
                return true;
            }
            if (keyCode == 256) { // Escape
                showTemplateSelector = false;
                return true;
            }
            return true;
        }

        if (showCatalystSelector) {
            if (keyCode == 259) { // Backspace
                if (!catalystSearchQuery.isEmpty()) {
                    catalystSearchQuery = catalystSearchQuery.substring(0, catalystSearchQuery.length() - 1);
                    updateCatalystFilter();
                }
                return true;
            }
            if (keyCode == 265) { // Up Arrow
                catalystScrollOffset = Math.max(0, catalystScrollOffset - 5);
                return true;
            }
            if (keyCode == 264) { // Down Arrow
                catalystScrollOffset = Math.min(Math.max(0, filteredCatalystIds.size() - 25), catalystScrollOffset + 5);
                return true;
            }
            if (keyCode == 256) { // Escape
                showCatalystSelector = false;
                return true;
            }
            return true;
        }

        if (showDetailsPopup) {
            if (keyCode == 256) { // Escape
                showDetailsPopup = false;
                return true;
            }
        }

        if (keyCode == 90 && hasControlDown()) {
            undo();
            return true;
        }
        if (keyCode == 89 && hasControlDown()) {
            redo();
            return true;
        }
        if (keyCode == 32) { // Spacebar recenters
            panX = 0;
            panY = 0;
            zoom = 1.0;
            return true;
        }
        if (keyCode == 45) { // Minus (-) zoom out
            double oldZoom = zoom;
            zoom = Math.max(0.5, Math.min(2.0, zoom - 0.1));
            double mouseCanvasX = (width / 2.0 - SIDEBAR_W - (width - SIDEBAR_W) / 2.0 - panX) / oldZoom;
            double mouseCanvasY = (height / 2.0 - height / 2.0 - panY) / oldZoom;
            panX = width / 2.0 - SIDEBAR_W - (width - SIDEBAR_W) / 2.0 - mouseCanvasX * zoom;
            panY = height / 2.0 - height / 2.0 - mouseCanvasY * zoom;
            return true;
        }
        if (keyCode == 61) { // Equal (=) zoom in
            double oldZoom = zoom;
            zoom = Math.max(0.5, Math.min(2.0, zoom + 0.1));
            double mouseCanvasX = (width / 2.0 - SIDEBAR_W - (width - SIDEBAR_W) / 2.0 - panX) / oldZoom;
            double mouseCanvasY = (height / 2.0 - height / 2.0 - panY) / oldZoom;
            panX = width / 2.0 - SIDEBAR_W - (width - SIDEBAR_W) / 2.0 - mouseCanvasX * zoom;
            panY = height / 2.0 - height / 2.0 - mouseCanvasY * zoom;
            return true;
        }
        if (keyCode == 48 && hasControlDown()) { // Ctrl + 0 reset zoom
            panX = 0;
            panY = 0;
            zoom = 1.0;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (showInputDialog) {
            inputDialogValue += codePoint;
            return true;
        }
        if (showTemplateSelector) {
            templateSearchQuery += codePoint;
            updateTemplateFilter();
            return true;
        }

        if (showCatalystSelector) {
            catalystSearchQuery += codePoint;
            updateCatalystFilter();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void saveTreeToServer() {
        if (activeTree == null) return;
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(activeTree);
        
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(activeBaseMountId);
        buf.writeUtf(json);
        NetworkManager.sendToServer(EvolutionPackets.C2S_SAVE_EVOLUTION_TREE, buf);
        isDirty = false;
    }

    @Override
    public void onClose() {
        if (isDirty) {
            showUnsavedChangesModal = true;
        } else {
            super.onClose();
        }
    }

    private static class ContextMenuItem {
        final String label;
        final Runnable action;

        ContextMenuItem(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    public static class Vec2d {
        final double x;
        final double y;
        Vec2d(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static class Vec2iOld {
        final int x;
        final int y;

        Vec2iOld(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private interface IEditorCommand {
        void execute();
        void undo();
    }

    private static class DetailRow {
        final String label;
        final String type;
        final Object data;
        
        DetailRow(String label, String type, Object data) {
            this.label = label;
            this.type = type;
            this.data = data;
        }
    }

    private static class CreateLinkCommand implements IEditorCommand {
        private final EvolutionTreeManager.EvolutionBranch branch;
        private final String oldParentId;
        private final String newParentId;
        private final AddonEvolutionEditorScreen screen;

        CreateLinkCommand(AddonEvolutionEditorScreen screen, EvolutionTreeManager.EvolutionBranch branch, String newParentId) {
            this.screen = screen;
            this.branch = branch;
            this.oldParentId = branch.parentId;
            this.newParentId = newParentId;
        }

        @Override
        public void execute() {
            branch.parentId = newParentId;
            screen.isDirty = true;
        }

        @Override
        public void undo() {
            branch.parentId = oldParentId;
            screen.isDirty = true;
        }
    }

    private static class UpdatePropertiesCommand implements IEditorCommand {
        private final EvolutionTreeManager.EvolutionBranch branch;
        private final String oldJson;
        private final String newJson;
        private final AddonEvolutionEditorScreen screen;

        UpdatePropertiesCommand(AddonEvolutionEditorScreen screen, EvolutionTreeManager.EvolutionBranch branch, String oldJson, String newJson) {
            this.screen = screen;
            this.branch = branch;
            this.oldJson = oldJson;
            this.newJson = newJson;
        }

        @Override
        public void execute() {
            EvolutionTreeManager.EvolutionBranch updated = new Gson().fromJson(newJson, EvolutionTreeManager.EvolutionBranch.class);
            applySnapshot(branch, updated);
            screen.isDirty = true;
        }

        @Override
        public void undo() {
            EvolutionTreeManager.EvolutionBranch old = new Gson().fromJson(oldJson, EvolutionTreeManager.EvolutionBranch.class);
            applySnapshot(branch, old);
            screen.isDirty = true;
        }
        
        private void applySnapshot(EvolutionTreeManager.EvolutionBranch dest, EvolutionTreeManager.EvolutionBranch src) {
            dest.displayName = src.displayName;
            dest.requiredBonding = src.requiredBonding;
            dest.requiredLevel = src.requiredLevel;
            dest.parentId = src.parentId;
            dest.excludes = new ArrayList<>(src.excludes);
            dest.conditions = src.conditions;
        }
    }
}
