package ddraig.net.rpgmounts.client.integration;

import ddraig.net.rpgmounts.RPGMounts;
import ddraig.net.rpgmounts.client.gui.AbilityCreatorScreen;
import ddraig.net.rpgmounts.client.gui.ConfigEditorScreen;
import ddraig.net.rpgmounts.client.gui.EnhancerCreatorScreen;
import ddraig.net.rpgmounts.client.gui.MountHUDScreen;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RPG Waypoints integration helper class
 * Handles detecting RPG Waypoints mod, sharing active WaypointThemes, and injecting custom HUD settings tab options.
 */
public class RPGWaypointsIntegration {
    private static boolean waypointsLoaded = false;
    private static boolean adminMode = false; // Toggled via admin-mode command

    // Active search field for mixin text forwarding
    public static net.minecraft.client.gui.components.EditBox activeSearchField = null;

    // Sync variables for audit logs
    public static final List<String> receivedAudits = new ArrayList<>();
    public static int totalAudits = 0;
    public static boolean auditsDirty = true;

    public static void setReceivedAudits(int total, List<String> list) {
        receivedAudits.clear();
        receivedAudits.addAll(list);
        totalAudits = total;
        auditsDirty = false;
    }

    public static void init() {
        waypointsLoaded = Platform.isModLoaded("rpgwaypoints") || Platform.isModLoaded("rpg_waypoints");
        if (waypointsLoaded) {
            RPGMounts.LOGGER.info("RPG Waypoints integration detected! Injecting tabs and syncing themes.");
            try {
                RPGWaypointsRegistrationHelper.registerTabs();
            } catch (Throwable e) {
                RPGMounts.LOGGER.error("Failed to register RPG Waypoints custom tabs", e);
            }
        }
    }

    public static void setAdminMode(boolean enabled) {
        adminMode = enabled;
    }

    public static boolean isAdminMode() {
        return adminMode;
    }

    public static boolean isWaypointsLoaded() {
        return waypointsLoaded;
    }

    public static int getThemeColor(String fieldName, int defaultColor) {
        if (!waypointsLoaded) return defaultColor;
        try {
            Class<?> themeClass = Class.forName("com.rpgwaypoints.compass.client.gui.WaypointTheme");
            Object theme = themeClass.getField("activeTheme").get(null);
            if (theme != null) {
                return themeClass.getField(fieldName).getInt(theme);
            }
        } catch (Throwable e) {
            // Fallback
        }
        return defaultColor;
    }

    public interface PanelHandler {
        void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTicks, int x, int y, int width, int height);
        boolean mouseClicked(double mouseX, double mouseY, int button);
        boolean mouseScrolled(double mouseX, double mouseY, double amount);
    }

    private static class RPGWaypointsRegistrationHelper {
        public static void registerTabs() {
            try {
                Class<?> apiClass = Class.forName("com.rpgwaypoints.compass.api.RPGWaypointsAPI");
                Class<?> customTabClass = Class.forName("com.rpgwaypoints.compass.api.RPGWaypointsAPI$CustomTab");
                Class<?> panelComponentClass = Class.forName("com.rpgwaypoints.compass.api.RPGWaypointsAPI$IRightPanelComponent");
                java.lang.reflect.Constructor<?> ctor = customTabClass.getConstructor(Component.class, panelComponentClass, java.util.function.Supplier.class);
                java.lang.reflect.Method regMethod = apiClass.getMethod("registerCustomTab", customTabClass);

                regMethod.invoke(null, ctor.newInstance(
                        Component.literal("Mounts"),
                        createPanelProxy(panelComponentClass, new MountsPanelComponent()),
                        (java.util.function.Supplier<Boolean>) () -> true
                ));

                regMethod.invoke(null, ctor.newInstance(
                        Component.literal("Mount Settings"),
                        createPanelProxy(panelComponentClass, new MountSettingsPanelComponent()),
                        (java.util.function.Supplier<Boolean>) () -> Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2) && adminMode
                ));

                regMethod.invoke(null, ctor.newInstance(
                        Component.literal("Enhancer Creator"),
                        createPanelProxy(panelComponentClass, new EnhancerCreatorPanelComponent()),
                        (java.util.function.Supplier<Boolean>) () -> Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2) && adminMode
                ));

                regMethod.invoke(null, ctor.newInstance(
                        Component.translatable("gui.rpg_mounts.ability_creator.title"),
                        createPanelProxy(panelComponentClass, new AbilityCreatorPanelHandler()),
                        (java.util.function.Supplier<Boolean>) () -> Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2) && adminMode
                ));

                regMethod.invoke(null, ctor.newInstance(
                        Component.literal("Audit Logs"),
                        createPanelProxy(panelComponentClass, new AuditLogsPanelComponent()),
                        (java.util.function.Supplier<Boolean>) () -> Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2) && adminMode
                ));
            } catch (Throwable e) {
                RPGMounts.LOGGER.error("Failed to register RPG Waypoints custom tabs", e);
            }
        }

        private static Object createPanelProxy(Class<?> panelComponentClass, PanelHandler handler) {
            return java.lang.reflect.Proxy.newProxyInstance(
                    panelComponentClass.getClassLoader(),
                    new Class<?>[]{panelComponentClass},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("render") && args != null && args.length == 8) {
                            handler.render((net.minecraft.client.gui.GuiGraphics) args[0],
                                    (Integer) args[1], (Integer) args[2], (Float) args[3],
                                    (Integer) args[4], (Integer) args[5], (Integer) args[6], (Integer) args[7]);
                            return null;
                        } else if (name.equals("mouseClicked") && args != null && args.length == 3) {
                            return handler.mouseClicked((Double) args[0], (Double) args[1], (Integer) args[2]);
                        } else if (name.equals("mouseScrolled") && args != null && args.length == 3) {
                            return handler.mouseScrolled((Double) args[0], (Double) args[1], (Double) args[2]);
                        } else if (name.equals("toString")) {
                            return handler.toString();
                        } else if (name.equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        } else if (name.equals("equals") && args != null && args.length == 1) {
                            return proxy == args[0];
                        }
                        return null;
                    }
            );
        }
    }

    private static class MountsPanelComponent implements PanelHandler {
        private MountHUDScreen hudScreenDelegate = null;
        private int lastX, lastY, lastW, lastH;

        private MountHUDScreen getDelegate() {
            if (hudScreenDelegate == null) {
                hudScreenDelegate = new MountHUDScreen();
            }
            return hudScreenDelegate;
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTicks, int x, int y, int width, int height) {
            this.lastX = x;
            this.lastY = y;
            this.lastW = width;
            this.lastH = height;
            MountHUDScreen delegate = getDelegate();
            if (!delegate.isInitialized()) {
                delegate.init(Minecraft.getInstance(), width, height);
            }
            g.fill(x, y, x + width, y + height, 0xFF101010);
            delegate.renderInTab(g, mouseX, mouseY, partialTicks, x, y, width, height);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return getDelegate().mouseClickedInTab(mouseX, mouseY, button, lastX, lastY, lastW, lastH);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            return getDelegate().mouseScrolledInTab(mouseX, mouseY, amount, lastX, lastY, lastW, lastH);
        }
    }

    private static class MountSettingsPanelComponent implements PanelHandler {
        private ConfigEditorScreen editorScreenDelegate = null;
        private int lastX, lastY, lastW, lastH;

        private ConfigEditorScreen getDelegate() {
            if (editorScreenDelegate == null) {
                editorScreenDelegate = new ConfigEditorScreen();
            }
            return editorScreenDelegate;
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTicks, int x, int y, int width, int height) {
            this.lastX = x;
            this.lastY = y;
            this.lastW = width;
            this.lastH = height;
            ConfigEditorScreen delegate = getDelegate();
            if (!delegate.isInitialized()) {
                delegate.init(Minecraft.getInstance(), width, height);
            }
            g.fill(x, y, x + width, y + height, 0xFF101010);
            delegate.renderInTab(g, mouseX, mouseY, partialTicks, x, y, width, height);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return getDelegate().mouseClickedInTab(mouseX, mouseY, button, lastX, lastY, lastW, lastH);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            return getDelegate().mouseScrolledInTab(mouseX, mouseY, amount, lastX, lastY, lastW, lastH);
        }
    }

    private static class EnhancerCreatorPanelComponent implements PanelHandler {
        private EnhancerCreatorScreen enhancerScreenDelegate = null;
        private int lastX, lastY, lastW, lastH;

        private EnhancerCreatorScreen getDelegate() {
            if (enhancerScreenDelegate == null) {
                enhancerScreenDelegate = new EnhancerCreatorScreen();
            }
            return enhancerScreenDelegate;
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTicks, int x, int y, int width, int height) {
            this.lastX = x;
            this.lastY = y;
            this.lastW = width;
            this.lastH = height;
            EnhancerCreatorScreen delegate = getDelegate();
            if (!delegate.isInitialized()) {
                delegate.init(Minecraft.getInstance(), width, height);
            }
            g.fill(x, y, x + width, y + height, 0xFF101010);
            delegate.renderInTab(g, mouseX, mouseY, partialTicks, x, y, width, height);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return getDelegate().mouseClickedInTab(mouseX, mouseY, button, lastX, lastY, lastW, lastH);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            return false;
        }
    }

    private static class AbilityCreatorPanelHandler implements PanelHandler {
        private AbilityCreatorScreen abilityScreenDelegate = null;
        private int lastX, lastY, lastW, lastH;

        private AbilityCreatorScreen getDelegate() {
            if (abilityScreenDelegate == null) {
                abilityScreenDelegate = new AbilityCreatorScreen();
            }
            return abilityScreenDelegate;
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTicks, int x, int y, int width, int height) {
            this.lastX = x;
            this.lastY = y;
            this.lastW = width;
            this.lastH = height;
            AbilityCreatorScreen delegate = getDelegate();
            if (!delegate.isInitialized()) {
                delegate.init(Minecraft.getInstance(), width, height);
            }
            g.fill(x, y, x + width, y + height, 0xFF101010);
            delegate.renderInTab(g, mouseX, mouseY, partialTicks, x, y, width, height);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return getDelegate().mouseClickedInTab(mouseX, mouseY, button, lastX, lastY, lastW, lastH);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            return false;
        }
    }

    private static class AuditLogsPanelComponent implements PanelHandler {
        private net.minecraft.client.gui.components.EditBox searchField = null;
        private int currentPage = 0;
        private final int pageSize = 15;
        private String lastQuery = "";
        private int lastPage = -1;
        private int lastX, lastY, lastW, lastH;

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTicks, int x, int y, int width, int height) {
            this.lastX = x;
            this.lastY = y;
            this.lastW = width;
            this.lastH = height;

            if (searchField == null) {
                searchField = new net.minecraft.client.gui.components.EditBox(Minecraft.getInstance().font, x + 10, y + 10, width - 20, 16, Component.literal("Search..."));
                searchField.setMaxLength(32);
            }

            // Keep positions updated in case layout shifts
            searchField.setX(x + 10);
            searchField.setY(y + 10);

            String query = searchField.getValue();
            if (currentPage != lastPage || !query.equals(lastQuery) || RPGWaypointsIntegration.auditsDirty) {
                lastPage = currentPage;
                lastQuery = query;
                RPGWaypointsIntegration.auditsDirty = false;
                // Send packet to request audit logs
                net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                buf.writeInt(currentPage);
                buf.writeInt(pageSize);
                buf.writeUtf(query);
                dev.architectury.networking.NetworkManager.sendToServer(ddraig.net.rpgmounts.network.ModPackets.C2S_REQUEST_AUDITS, buf);
            }

            // Render Background
            g.fill(x, y, x + width, y + height, 0xFF101010);
            
            // Draw Search Box
            searchField.render(g, mouseX, mouseY, partialTicks);

            // Draw logs
            int lineY = y + 32;
            synchronized (RPGWaypointsIntegration.receivedAudits) {
                for (int i = 0; i < RPGWaypointsIntegration.receivedAudits.size(); i++) {
                    String log = RPGWaypointsIntegration.receivedAudits.get(i);
                    String truncated = Minecraft.getInstance().font.plainSubstrByWidth(log, width - 20);
                    g.drawString(Minecraft.getInstance().font, truncated, x + 10, lineY + i * 9, 0xFFD4AF37, false);
                }
            }

            // Draw Pagination
            int maxPage = Math.max(0, (RPGWaypointsIntegration.totalAudits - 1) / pageSize);
            String pageText = "Page " + (currentPage + 1) + " of " + (maxPage + 1);
            int textW = Minecraft.getInstance().font.width(pageText);
            g.drawString(Minecraft.getInstance().font, pageText, x + (width - textW) / 2, y + height - 18, 0xFFE0E0E0, false);

            // Prev Button [<]
            int btnY = y + height - 22;
            g.fill(x + 10, btnY, x + 26, btnY + 16, currentPage > 0 ? 0xFF333333 : 0xFF1A1A1A);
            g.drawString(Minecraft.getInstance().font, "<", x + 16, btnY + 4, currentPage > 0 ? 0xFFFFFFFF : 0xFF888888, false);

            // Next Button [>]
            g.fill(x + width - 26, btnY, x + width - 10, btnY + 16, currentPage < maxPage ? 0xFF333333 : 0xFF1A1A1A);
            g.drawString(Minecraft.getInstance().font, ">", x + width - 20, btnY + 4, currentPage < maxPage ? 0xFFFFFFFF : 0xFF888888, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (searchField != null) {
                if (searchField.mouseClicked(mouseX, mouseY, button)) {
                    searchField.setFocused(true);
                    RPGWaypointsIntegration.activeSearchField = searchField;
                    return true;
                } else {
                    searchField.setFocused(false);
                    if (RPGWaypointsIntegration.activeSearchField == searchField) {
                        RPGWaypointsIntegration.activeSearchField = null;
                    }
                }
            }

            int btnY = lastY + lastH - 22;
            if (mouseX >= lastX + 10 && mouseX <= lastX + 26 && mouseY >= btnY && mouseY <= btnY + 16) {
                if (currentPage > 0) {
                    currentPage--;
                    RPGWaypointsIntegration.auditsDirty = true;
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true;
            }

            if (mouseX >= lastX + lastW - 26 && mouseX <= lastX + lastW - 10 && mouseY >= btnY && mouseY <= btnY + 16) {
                int maxPage = Math.max(0, (RPGWaypointsIntegration.totalAudits - 1) / pageSize);
                if (currentPage < maxPage) {
                    currentPage++;
                    RPGWaypointsIntegration.auditsDirty = true;
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            return false;
        }
    }
}
