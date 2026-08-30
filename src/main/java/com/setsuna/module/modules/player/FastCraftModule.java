package com.setsuna.module.modules.player;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.IntSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows a quick-craft panel beside the inventory screen.
 * <p>
 * Listens for craft hints in chat messages ("you have all the items to craft..."),
 * maintains a list of known craftable items, and renders a scrollable grid
 * panel beside the inventory GUI. Clicking an item sends
 * {@code /quickcraft <item-id>} to the server.
 */
public final class FastCraftModule extends Module {

    public static final FastCraftModule INSTANCE = new FastCraftModule();

    // -- settings -----------------------------------------------------------------

    public final IntSetting columns = add(new IntSetting("Columns", 4, 2, 6, 1));
    public final IntSetting rows = add(new IntSetting("Rows", 3, 1, 6, 1));
    public final IntSetting padding = add(new IntSetting("Padding", 6, 2, 10, 1));
    public final BooleanSetting smartFilter = add(new BooleanSetting("Smart Filter", true));
    public final ColorSetting backgroundColor = add(new ColorSetting("Background Color", new Color(18, 18, 24, 176)));
    public final ColorSetting slotColor = add(new ColorSetting("Slot Color", new Color(255, 255, 255, 20)));
    public final ColorSetting hoverColor = add(new ColorSetting("Hover Color", new Color(255, 255, 255, 46)));
    public final ColorSetting titleColor = add(new ColorSetting("Title Color", new Color(215, 227, 240, 255)));

    // -- regex patterns ----------------------------------------------------------

    private static final Pattern CRAFT_HINT_PATTERN = Pattern.compile(
            "you have all the items to craft (?:a |an |the )?(.+?)(?= click here| to craft it!?|!|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CRAFTED_PATTERN = Pattern.compile(
            "(?:! )?you crafted (.+?) \\((\\d+)/(\\d+)\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RESET_PATTERN = Pattern.compile(
            "welcome to hoplite!|you have joined a server",
            Pattern.CASE_INSENSITIVE);

    // -- internal state -----------------------------------------------------------

    /** Catalog entries keyed by their normalised display name. */
    private final Map<String, ItemEntry> itemByName = new ConcurrentHashMap<>();

    /** Catalog and fallback entries keyed by quick-craft ID. */
    private final Map<String, ItemEntry> itemById = new ConcurrentHashMap<>();

    /** Items in the server's stable quick-craft order. */
    private final List<ItemEntry> catalogItems = new CopyOnWriteArrayList<>();

    /** IDs discovered through the optional vanilla fallback, scoped to a session. */
    private final Set<String> dynamicItemIds = ConcurrentHashMap.newKeySet();

    /** Item names that have appeared in craft hints in chat. */
    private final Set<String> knownItems = ConcurrentHashMap.newKeySet();

    /** Item IDs that have been fully crafted (reached max count). */
    private final Set<String> craftedItems = ConcurrentHashMap.newKeySet();

    /** Recent chat fragments, used for multi-line detection. */
    private final Deque<String> recentMessages = new ConcurrentLinkedDeque<>();

    /** Current page index. */
    private volatile int currentPage;

    /** Whether the item index has been populated. */
    private boolean itemIndexBuilt;

    private FastCraftModule() {
        super("FastCraft", Category.PLAYER);
    }

    // -- item index ---------------------------------------------------------------

    /** Ensures the item lookup maps are built. Must be called after vanilla registries are ready. */
    private synchronized void ensureItemIndex() {
        if (itemIndexBuilt) return;
        buildItemIndex();
        itemIndexBuilt = true;
    }

    /** Builds the Hoplite catalog and the lookup maps used by chat parsing. */
    private void buildItemIndex() {
        itemByName.clear();
        itemById.clear();
        catalogItems.clear();
        dynamicItemIds.clear();
        for (FastCraftCatalog.Definition definition : FastCraftCatalog.entries()) {
            ItemEntry entry = new ItemEntry(
                    definition.id(),
                    normalizeName(definition.displayName()),
                    FastCraftCatalog.createStack(definition));
            catalogItems.add(entry);
            itemById.put(entry.id, entry);
            itemByName.put(entry.displayName, entry);
        }
    }

    // -- lifecycle ----------------------------------------------------------------

    @Override
    protected void onEnable() {
        super.onEnable();
        reset();
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        reset();
    }

    // -- packet listener ----------------------------------------------------------

    @Listen
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundSystemChatPacket packet)) {
            return;
        }
        ensureItemIndex();
        if (packet.overlay()) {
            return;
        }
        if (packet.content() == null) {
            return;
        }

        // Check for click events in the text component (run_command with /quickcraft)
        if (extractClickCommands(packet.content())) {
            recentMessages.clear();
            return;
        }

        String raw = packet.content().getString();
        String clean = cleanMessage(raw);
        if (clean.isEmpty()) {
            return;
        }

        // Reset on server join
        if (RESET_PATTERN.matcher(clean).find()) {
            reset();
            return;
        }

        // Check for "you crafted ... (x/y)" and track completed crafts.
        if (handleCraftedMessage(clean)) {
            recentMessages.clear();
            return;
        }

        // Check for "you have all the items to craft ..."
        if (handleCraftHint(clean)) {
            recentMessages.clear();
            return;
        }

        // Accumulate messages for multi-line detection
        recentMessages.addLast(clean);
        if (recentMessages.size() > 6) {
            recentMessages.pollFirst();
        }

        // Re-check accumulated buffer
        String combined = combineMessages();
        if (!combined.isEmpty() && handleCraftHint(combined)) {
            recentMessages.clear();
        }
    }

    // -- chat parsing -------------------------------------------------------------

    /**
     * Extracts /quickcraft command targets from click events in rich-text messages.
     */
    private boolean extractClickCommands(net.minecraft.network.chat.Component text) {
        if (text == null) return false;
        Set<String> found = new HashSet<>();
        Deque<net.minecraft.network.chat.Component> stack = new ArrayDeque<>();
        stack.push(text);
        while (!stack.isEmpty()) {
            net.minecraft.network.chat.Component comp = stack.pop();
            var style = comp.getStyle();
            var clickEvent = style != null ? style.getClickEvent() : null;
            if (clickEvent instanceof net.minecraft.network.chat.ClickEvent.RunCommand runCmd) {
                String itemId = extractQuickCraftItem(runCmd.command());
                if (itemId != null) {
                    found.add(itemId);
                }
            }
            List<net.minecraft.network.chat.Component> siblings = comp.getSiblings();
            for (int i = siblings.size() - 1; i >= 0; i--) {
                stack.push(siblings.get(i));
            }
        }
        if (found.isEmpty()) return false;
        boolean newItems = false;
        for (String id : found) {
            if (id == null || id.isEmpty()) {
                continue;
            }
            String canonicalId = canonicalId(id);
            ItemEntry entry = ensureEntryForId(canonicalId);
            String trackedId = entry != null ? entry.id : canonicalId;
            if (!craftedItems.contains(trackedId)) {
                knownItems.add(trackedId);
                newItems = true;
            }
        }
        return newItems;
    }

    @Nullable
    private String extractQuickCraftItem(String command) {
        if (command == null) return null;
        String trimmed = command.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("quickcraft ")) {
            return null;
        }
        String itemPart = trimmed.substring("quickcraft ".length()).trim();
        if (itemPart.isEmpty()) return null;
        String[] parts = itemPart.split("\\s+");
        return parts.length > 0 ? parts[0] : null;
    }

    private boolean handleCraftHint(String message) {
        Matcher matcher = CRAFT_HINT_PATTERN.matcher(message);
        if (!matcher.find()) return false;
        String itemName = matcher.group(1).trim();
        String itemId = resolveItemId(itemName);
        if (itemId == null || craftedItems.contains(itemId)) return false;
        ItemEntry entry = ensureEntryForId(itemId);
        if (entry == null) return false;
        knownItems.add(entry.id);
        return true;
    }

    private boolean handleCraftedMessage(String message) {
        Matcher matcher = CRAFTED_PATTERN.matcher(message);
        if (!matcher.find()) return false;
        String itemName = matcher.group(1).trim();
        int current;
        int max;
        try {
            current = Integer.parseInt(matcher.group(2));
            max = Integer.parseInt(matcher.group(3));
        } catch (NumberFormatException ignored) {
            return true;
        }
        String itemId = resolveItemId(itemName);
        if (itemId == null) return true;
        if (current >= max) {
            craftedItems.add(itemId);
            knownItems.remove(itemId);
        }
        return true;
    }

    @Nullable
    private String resolveItemId(String displayName) {
        String key = normalizeName(displayName);
        if (key.isEmpty()) return null;

        // Direct lookup
        ItemEntry entry = itemByName.get(key);
        if (entry != null) return entry.id;

        // Partial match
        for (ItemEntry e : catalogItems) {
            if (key.contains(e.displayName) || e.displayName.contains(key)) {
                return e.id;
            }
        }

        // A server may add a vanilla-named quick-craft entry not present in the
        // bundled Hoplite catalog. Resolve it lazily without indexing every item.
        for (Item item : BuiltInRegistries.ITEM) {
            String itemName = normalizeName(new ItemStack(item).getHoverName().getString());
            if (itemName.isEmpty()) {
                continue;
            }
            if (!key.equals(itemName) && !key.contains(itemName) && !itemName.contains(key)) {
                continue;
            }
            Identifier identifier = BuiltInRegistries.ITEM.getKey(item);
            if (identifier == null) continue;
            ItemEntry dynamic = ensureEntryForId(identifier.toString());
            if (dynamic != null) return dynamic.id;
        }
        return null;
    }

    private String normalizeName(String name) {
        if (name == null) return "";
        return cleanMessage(name.replace("@", ""));
    }

    /** Returns a canonical lower-case command ID and strips an optional slash. */
    private static String canonicalId(String id) {
        String value = id == null ? "" : id.trim();
        if (value.startsWith("/")) {
            value = value.substring(1).trim();
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * Resolves a command ID to a catalog entry, with a vanilla fallback for
     * servers that use a standard item as a quick-craft result.
     */
    @Nullable
    private ItemEntry ensureEntryForId(String id) {
        if (id == null || id.isEmpty()) return null;
        ensureItemIndex();

        String canonical = canonicalId(id);
        ItemEntry known = itemById.get(canonical);
        if (known != null) return known;

        Identifier identifier = Identifier.tryParse(canonical);
        if (identifier == null) return null;
        Item item = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
        if (item == null) return null;

        ItemStack stack = new ItemStack(item);
        String displayName = normalizeName(stack.getHoverName().getString());
        ItemEntry entry = new ItemEntry(canonical, displayName, stack);
        itemById.put(canonical, entry);
        itemByName.putIfAbsent(displayName, entry);
        if (!catalogItems.contains(entry)) {
            catalogItems.add(entry);
        }
        dynamicItemIds.add(canonical);
        return entry;
    }

    // -- message utilities --------------------------------------------------------

    /** Strips color codes and normalises whitespace. */
    private static String cleanMessage(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        boolean lastWasSpace = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\u00a7') {
                i++; // skip colour code
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (lastWasSpace) continue;
                sb.append(' ');
                lastWasSpace = true;
                continue;
            }
            if (c == '\u2019' || c == '\u2018' || c == '`') {
                c = '\'';
            }
            sb.append(Character.toLowerCase(c));
            lastWasSpace = false;
        }
        // Trim trailing space
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == ' ') {
            sb.setLength(len - 1);
        }
        return sb.toString();
    }

    private static String stripFormatting(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7') {
                i++; // skip next char
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String combineMessages() {
        if (recentMessages.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(160);
        for (String msg : recentMessages) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(msg);
        }
        return sb.toString();
    }

    // -- rendering ----------------------------------------------------------------

    /**
     * Renders the FastCraft panel. Called from the mixin at the end of
     * {@link net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen#extractRenderState}.
     */
    public void render(GuiGraphicsExtractor graphics, int screenLeft, int screenTop,
                       int mouseX, int mouseY) {
        if (mc.player == null) return;
        ensureItemIndex();

        List<ItemEntry> items = getFilteredItems();
        int colCount = columns.get();
        int rowCount = rows.get();
        int padAmount = padding.get();

        int slotSize = 18;
        int panelW = colCount * slotSize + Math.max(0, colCount - 1) * 4 + padAmount * 2;
        int panelH = 19 + rowCount * slotSize + Math.max(0, rowCount - 1) * 4 + padAmount * 2 + 17;
        int pageSize = colCount * rowCount;
        int maxPage = pageSize <= 0 || items.isEmpty() ? 0 : (items.size() - 1) / pageSize;
        currentPage = Math.min(currentPage, maxPage);

        int panelX = computePanelX(screenLeft, panelW);
        int panelY = screenTop;

        // Clamp page
        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(items.size(), startIndex + pageSize);

        // Draw background
        drawPanelBackground(graphics, panelX, panelY, panelW, panelH);

        // Draw title
        String title = "FastCraft";
        graphics.text(mc.font, title, panelX + padAmount, panelY + 6, titleColor.get().getRGB(), false);

        // Draw count
        String count = String.valueOf(items.size());
        int countX = panelX + panelW - padAmount - mc.font.width(count);
        graphics.text(mc.font, count, countX, panelY + 6, 0xFF9A9A9A, false);

        // Draw item slots
        ItemEntry hoveredEntry = null;
        for (int i = startIndex; i < endIndex; i++) {
            ItemEntry entry = items.get(i);
            int index = i - startIndex;
            int col = index % colCount;
            int row = index / colCount;
            int slotX = panelX + padAmount + col * 22;
            int slotY = panelY + 19 + padAmount + row * 22;

            boolean hovered = isHovered(mouseX, mouseY, slotX, slotY, slotSize, slotSize);
            boolean crafted = craftedItems.contains(entry.id);
            if (hovered) {
                hoveredEntry = entry;
            }

            // Draw slot background
            int color;
            if (crafted) {
                color = 0x50FF5555; // red tint for crafted
            } else if (hovered) {
                color = hoverColor.get().getRGB();
            } else {
                color = slotColor.get().getRGB();
            }
            graphics.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, color);

            // Render item
            ItemStack stack = entry.stack;
            graphics.item(stack, slotX + 1, slotY + 1);
        }

        if (hoveredEntry != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(hoveredEntry.stack.getHoverName());
            if (craftedItems.contains(hoveredEntry.id)) {
                tooltip.add(Component.literal("Craft limit reached"));
            }
            graphics.setComponentTooltipForNextFrame(mc.font, tooltip, mouseX, mouseY);
        }

        // Draw page navigation
        int navY = panelY + panelH - 17;
        int prevX = panelX + padAmount - 2;
        int nextX = panelX + panelW - padAmount - 12 + 2;

        boolean canPrev = currentPage > 0;
        boolean canNext = currentPage < maxPage;

        int navColor = 0xFFB0B0B0;
        // Previous page.
        drawNavButton(graphics, "<", prevX, navY + 2, 12, 12, canPrev ? navColor : 0xFF717171);

        // Next page.
        drawNavButton(graphics, ">", nextX, navY + 2, 12, 12, canNext ? navColor : 0xFF717171);

        // Page indicator
        String pageText = (currentPage + 1) + "/" + (maxPage + 1);
        int pageTextWidth = mc.font.width(pageText);
        graphics.text(mc.font, pageText,
                panelX + panelW / 2 - pageTextWidth / 2,
                navY + 5, 0xFFC8C8C8, false);

        // "Empty" / "Waiting" text
        if (items.isEmpty()) {
            String emptyText = smartFilter.get() ? "Waiting" : "Empty";
            int tw = mc.font.width(emptyText);
            int tx = panelX + panelW / 2 - tw / 2;
            int ty = panelY + 19 + padAmount
                    + (rowCount * slotSize + (rowCount - 1) * 4) / 2 - 4;
            graphics.text(mc.font, emptyText, tx, ty, 0xFF8C8C8C, false);
        }
    }

    private int computePanelX(int screenLeft, int panelW) {
        // OpenClap places the panel on the left whenever there is room. This
        // keeps it beside the inventory even when the recipe book is expanded.
        int left = screenLeft - panelW - 7;
        if (left >= 4) {
            return left;
        }

        int right = screenLeft + 176 + 7;
        int width = mc.getWindow().getGuiScaledWidth();
        if (right + panelW <= width - 4) {
            return right;
        }

        // Very small GUI scales can leave no side with enough room. Keep the
        // panel visible rather than allowing it to be clipped off-screen.
        return Math.max(2, Math.min(right, width - panelW - 2));
    }

    // -- click handling -----------------------------------------------------------

    /**
     * Handles mouse clicks on the FastCraft panel.
     *
     * @return true if the click was consumed by the panel
     */
    public boolean mouseClicked(double x, double y, int screenLeft, int screenTop) {
        if (mc.player == null) return false;
        ensureItemIndex();

        int colCount = columns.get();
        int rowCount = rows.get();
        int padAmount = padding.get();
        int slotSize = 18;
        int panelW = colCount * slotSize + Math.max(0, colCount - 1) * 4 + padAmount * 2;
        int panelH = 19 + rowCount * slotSize + Math.max(0, rowCount - 1) * 4 + padAmount * 2 + 17;
        int panelX = computePanelX(screenLeft, panelW);
        int panelY = screenTop;

        // Check if click is within panel bounds
        if (x < panelX || x >= panelX + panelW || y < panelY || y >= panelY + panelH) {
            return false;
        }

        List<ItemEntry> items = getFilteredItems();
        int pageSize = colCount * rowCount;
        int maxPage = pageSize <= 0 || items.isEmpty() ? 0 : (items.size() - 1) / pageSize;
        currentPage = Math.min(currentPage, maxPage);
        int navY = panelY + panelH - 17;
        int prevX = panelX + padAmount - 2;
        int nextX = panelX + panelW - padAmount - 12 + 2;
        if (isHovered(x, y, prevX, navY, 12, 16)) {
            prevPage();
            return true;
        }
        if (isHovered(x, y, nextX, navY, 12, 16)) {
            nextPage();
            return true;
        }

        // Check item slots
        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(items.size(), startIndex + pageSize);

        for (int i = startIndex; i < endIndex; i++) {
            ItemEntry entry = items.get(i);
            int index = i - startIndex;
            int col = index % colCount;
            int row = index / colCount;
            int slotX = panelX + padAmount + col * 22;
            int slotY = panelY + 19 + padAmount + row * 22;

            if (isHovered(x, y, slotX, slotY, slotSize, slotSize)) {
                sendCraftCommand(entry.id);
                return true;
            }
        }

        return false;
    }

    private void sendCraftCommand(String itemId) {
        if (itemId == null || mc.getConnection() == null) return;
        mc.getConnection().sendCommand("quickcraft " + itemId);
    }

    // -- pagination ---------------------------------------------------------------

    private void nextPage() {
        int pageSize = columns.get() * rows.get();
        List<ItemEntry> items = getFilteredItems();
        int maxPage = pageSize <= 0 || items.isEmpty() ? 0 : (items.size() - 1) / pageSize;
        currentPage = Math.min(currentPage + 1, maxPage);
    }

    private void prevPage() {
        currentPage = Math.max(currentPage - 1, 0);
    }

    // -- helpers ------------------------------------------------------------------

    private List<ItemEntry> getFilteredItems() {
        List<ItemEntry> all = new ArrayList<>(catalogItems);
        if (!smartFilter.get()) {
            return all;
        }
        // Smart filter: only show items that have appeared in chat, preserving
        // the server catalog order used by OpenClap.
        List<ItemEntry> filtered = new ArrayList<>();
        for (ItemEntry entry : all) {
            if (craftedItems.contains(entry.id)) continue;
            if (knownItems.contains(entry.id)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private static boolean isHovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawPanelBackground(GuiGraphicsExtractor graphics, int x, int y,
                                     int w, int h) {
        int bg = backgroundColor.get().getRGB();
        int headerLine = 0x50FFFFFF;  // separator line below title
        int footerLine = 0x18FFFFFF;  // separator line above nav

        graphics.fill(x, y, x + w, y + h, bg);

        // Title separator
        graphics.fill(x + 1, y + 19, x + w - 1, (int) (y + 19 + 1), headerLine);

        // Footer separator
        graphics.fill(x + 1, y + h - 17, x + w - 1, (int) (y + h - 17 + 1), footerLine);
    }

    private void drawNavButton(GuiGraphicsExtractor graphics, String text,
                               int x, int y, int w, int h, int color) {
        int tw = mc.font.width(text);
        float tx = x + (w - tw) / 2.0F;
        float ty = y + (h - mc.font.lineHeight) / 2.0F;
        graphics.text(mc.font, text, (int) tx, (int) ty, color, false);
    }

    private void reset() {
        knownItems.clear();
        craftedItems.clear();
        recentMessages.clear();
        currentPage = 0;

        // Vanilla fallback entries belong to the server session; do not carry
        // them into the next server while retaining the static Hoplite catalog.
        if (!dynamicItemIds.isEmpty()) {
            for (String id : dynamicItemIds) {
                ItemEntry entry = itemById.remove(id);
                if (entry != null) {
                    catalogItems.remove(entry);
                    itemByName.remove(entry.displayName, entry);
                }
            }
            dynamicItemIds.clear();
        }
    }

    // -- data types ---------------------------------------------------------------

    /** Internal item entry. */
    private record ItemEntry(String id, String displayName, ItemStack stack) {
    }

}
