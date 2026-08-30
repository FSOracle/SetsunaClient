package com.setsuna.ui.hud;

import com.setsuna.event.Listen;
import com.setsuna.event.Priority;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.PathDirection;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Builds and renders true path-union surfaces for touching HUD elements. */
public final class HudFusionManager {

    public static final HudFusionManager INSTANCE = new HudFusionManager();

    private static final float TOUCH_TOLERANCE = 4.0F;

    private final Set<EpsilonHudModule> fusedThisFrame =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final IdentityHashMap<EpsilonHudModule, FusionSelection> selectionsThisFrame =
            new IdentityHashMap<>();
    private Render2DEvent preparedEvent;

    private HudFusionManager() {
    }

    /** Draw fused surfaces before the individual modules draw their content. */
    @Listen(priority = Priority.HIGHEST)
    private void renderFusedGroups(Render2DEvent event) {
        preparedEvent = event;
        fusedThisFrame.clear();
        selectionsThisFrame.clear();
        for (Group group : groups(event.width(), event.height())) {
            fusedThisFrame.addAll(group.members());
            FusionSelection selection = layout(group, event.width(), event.height());
            for (EpsilonHudModule member : group.members()) {
                selectionsThisFrame.put(member, selection);
            }
            drawGroup(event.canvas(), group, selection);
        }
    }

    static boolean isFused(EpsilonHudModule module, Render2DEvent event) {
        return INSTANCE.preparedEvent == event && INSTANCE.fusedThisFrame.contains(module);
    }

    static FusionSelection selection(EpsilonHudModule module,
                                     float screenWidth, float screenHeight) {
        for (Group group : INSTANCE.groups(screenWidth, screenHeight)) {
            if (group.members().contains(module)) {
                return layout(group, screenWidth, screenHeight);
            }
        }
        Rect bounds = Rect.makeXYWH(module.hudX(screenWidth), module.hudY(screenHeight),
                module.hudWidth(screenWidth), module.hudHeight(screenHeight));
        return new FusionSelection(module, List.of(module),
                List.of(new FusionCell(module, bounds)), bounds, false);
    }

    static Rect contentBounds(EpsilonHudModule module, Render2DEvent event) {
        FusionSelection selection = INSTANCE.preparedEvent == event
                ? INSTANCE.selectionsThisFrame.get(module) : null;
        if (selection == null) {
            selection = selection(module, event.width(), event.height());
        }
        return selection.cellBounds(module);
    }

    static Edges edges(EpsilonHudModule module, float screenWidth, float screenHeight) {
        for (Group group : INSTANCE.groups(screenWidth, screenHeight)) {
            if (!group.members().contains(module)) continue;
            return attachedEdges(module, group.members(), screenWidth, screenHeight);
        }
        return Edges.NONE;
    }

    private static void drawGroup(Canvas canvas, Group group, FusionSelection selection) {
        EpsilonHudModule owner = group.owner();
        FusionStyle style = owner.hudFusionStyle();
        if (style == null) return;

        Rect bounds = selection.bounds();
        try (Path shape = groupPath(bounds, style.radius())) {
            if (shape == null) return;
            int layer = owner.opacityAlpha() >= 255
                    ? canvas.save() : canvas.saveLayerAlpha(bounds, owner.opacityAlpha());
            try {
                if (style.blur()) {
                    SkijaRenderer.drawBlurredBackdrop(canvas, shape,
                            bounds.getLeft(), bounds.getTop(), bounds.getWidth(), bounds.getHeight(),
                            style.blurStrength());
                }
                if (style.shadow()) {
                    SkijaUi.dropShadowPath(canvas, shape, style.shadowStrength(), 0x9C000000);
                }
                if (style.background()) {
                    HudRenderUtil.coloredPath(canvas, shape, style.backgroundColor());
                }
                if (selection.compactStrip()) {
                    drawSeparators(canvas, selection);
                }
                if (style.border()) {
                    HudRenderUtil.borderPath(canvas, shape, bounds, style.borderWidth(),
                            style.borderMode(), style.borderColor(), style.borderStart(),
                            style.borderEnd(), 255);
                }
            } finally {
                canvas.restoreToCount(layer);
            }
        }
    }

    private static void drawSeparators(Canvas canvas, FusionSelection selection) {
        float top = selection.bounds().getTop();
        float height = selection.bounds().getHeight();
        float margin = Math.max(3.0F, Math.min(7.0F, height * 0.24F));
        float lineHeight = Math.max(1.0F, height - margin * 2.0F);
        int color = UiTheme.withAlpha(UiTheme.TEXT_FAINT, 104);
        for (int index = 1; index < selection.cells().size(); index++) {
            float x = selection.cells().get(index).bounds().getLeft();
            HudRenderUtil.hairline(canvas, x - 0.5F, top + margin, 1.0F, lineHeight, color);
        }
    }

    private List<Group> groups(float screenWidth, float screenHeight) {
        List<EpsilonHudModule> candidates = candidates();
        Set<EpsilonHudModule> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Group> result = new ArrayList<>();
        for (EpsilonHudModule candidate : candidates) {
            if (!visited.add(candidate)) continue;
            List<EpsilonHudModule> members = new ArrayList<>();
            ArrayDeque<EpsilonHudModule> pending = new ArrayDeque<>();
            pending.add(candidate);
            while (!pending.isEmpty()) {
                EpsilonHudModule current = pending.removeFirst();
                members.add(current);
                for (EpsilonHudModule other : candidates) {
                    if (visited.contains(other)) continue;
                    if (touches(current, other, screenWidth, screenHeight)) {
                        visited.add(other);
                        pending.addLast(other);
                    }
                }
            }
            if (members.size() > 1) {
                result.add(new Group(List.copyOf(members), largest(members, screenWidth, screenHeight)));
            }
        }
        return result;
    }

    private static List<EpsilonHudModule> candidates() {
        List<EpsilonHudModule> result = new ArrayList<>();
        for (Module module : ModuleManager.INSTANCE.modules()) {
            if (module instanceof EpsilonHudModule hud && hud.isEnabled()
                    && hud.supportsHudFusion() && hud.hudFusionStyle() != null) {
                result.add(hud);
            }
        }
        return result;
    }

    private static EpsilonHudModule largest(List<EpsilonHudModule> members,
                                            float screenWidth, float screenHeight) {
        EpsilonHudModule largest = members.getFirst();
        float largestArea = area(largest, screenWidth, screenHeight);
        for (int index = 1; index < members.size(); index++) {
            EpsilonHudModule candidate = members.get(index);
            float area = area(candidate, screenWidth, screenHeight);
            if (area > largestArea) {
                largest = candidate;
                largestArea = area;
            }
        }
        return largest;
    }

    private static float area(EpsilonHudModule module, float screenWidth, float screenHeight) {
        return module.hudWidth(screenWidth) * module.hudHeight(screenHeight);
    }

    private static Path groupPath(Rect bounds, float radius) {
        float safeRadius = Math.max(0.0F,
                Math.min(radius, Math.min(bounds.getWidth(), bounds.getHeight()) * 0.5F));
        try (PathBuilder builder = new PathBuilder()) {
            builder.addRRect(RRect.makeXYWH(bounds.getLeft(), bounds.getTop(),
                    bounds.getWidth(), bounds.getHeight(), safeRadius),
                    PathDirection.CLOCKWISE, 0);
            return builder.detach();
        }
    }

    private static FusionSelection layout(Group group, float screenWidth, float screenHeight) {
        boolean compactStrip = group.members().stream().allMatch(HudFusionManager::compactReadout);
        if (!compactStrip) {
            List<FusionCell> cells = new ArrayList<>();
            for (EpsilonHudModule member : group.members()) {
                cells.add(new FusionCell(member,
                        joinedBounds(member, group.members(), screenWidth, screenHeight)));
            }
            return new FusionSelection(group.owner(), group.members(), List.copyOf(cells),
                    groupBounds(group.members(), screenWidth, screenHeight), false);
        }

        List<EpsilonHudModule> ordered = new ArrayList<>(group.members());
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (EpsilonHudModule member : ordered) {
            float x = member.hudX(screenWidth);
            float y = member.hudY(screenHeight);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        Comparator<EpsilonHudModule> order = maxX - minX >= maxY - minY
                ? Comparator.comparingDouble(module -> module.hudX(screenWidth))
                : Comparator.comparingDouble(module -> module.hudY(screenHeight));
        ordered.sort(order.thenComparing(Module::name));

        float stripHeight = 0.0F;
        for (EpsilonHudModule member : ordered) {
            stripHeight = Math.max(stripHeight, member.hudFusionContentHeight(screenHeight));
        }
        List<FusionCell> cells = new ArrayList<>();
        float cursor = minX;
        for (EpsilonHudModule member : ordered) {
            float cellWidth = member.hudFusionContentWidth(screenWidth);
            Rect cell = Rect.makeXYWH(cursor, minY, cellWidth, stripHeight);
            cells.add(new FusionCell(member, cell));
            cursor += cellWidth;
        }
        Rect bounds = Rect.makeXYWH(minX, minY, Math.max(1.0F, cursor - minX), stripHeight);
        return new FusionSelection(group.owner(), List.copyOf(ordered),
                List.copyOf(cells), bounds, true);
    }

    private static Rect joinedBounds(EpsilonHudModule module, List<EpsilonHudModule> members,
                                     float screenWidth, float screenHeight) {
        float left = module.hudX(screenWidth);
        float top = module.hudY(screenHeight);
        float right = left + module.hudWidth(screenWidth);
        float bottom = top + module.hudHeight(screenHeight);
        for (EpsilonHudModule other : members) {
            if (other == module) continue;
            float otherLeft = other.hudX(screenWidth);
            float otherTop = other.hudY(screenHeight);
            float otherRight = otherLeft + other.hudWidth(screenWidth);
            float otherBottom = otherTop + other.hudHeight(screenHeight);
            if (sameSpan(top, bottom, otherTop, otherBottom)) {
                if (close(right, otherLeft)) right = Math.max(right, otherLeft);
                if (close(left, otherRight)) left = Math.min(left, otherRight);
            }
            if (sameSpan(left, right, otherLeft, otherRight)) {
                if (close(bottom, otherTop)) bottom = Math.max(bottom, otherTop);
                if (close(top, otherBottom)) top = Math.min(top, otherBottom);
            }
        }
        return Rect.makeLTRB(left, top, right, bottom);
    }

    private static Rect groupBounds(List<EpsilonHudModule> members,
                                    float screenWidth, float screenHeight) {
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (EpsilonHudModule member : members) {
            Rect bounds = joinedBounds(member, members, screenWidth, screenHeight);
            left = Math.min(left, bounds.getLeft());
            top = Math.min(top, bounds.getTop());
            right = Math.max(right, bounds.getRight());
            bottom = Math.max(bottom, bounds.getBottom());
        }
        return Rect.makeLTRB(left, top, right, bottom);
    }

    private static Edges attachedEdges(EpsilonHudModule module, List<EpsilonHudModule> members,
                                       float screenWidth, float screenHeight) {
        float x = module.hudX(screenWidth);
        float y = module.hudY(screenHeight);
        float width = module.hudWidth(screenWidth);
        float height = module.hudHeight(screenHeight);
        boolean left = false;
        boolean right = false;
        boolean top = false;
        boolean bottom = false;
        for (EpsilonHudModule other : members) {
            if (other == module) continue;
            float otherX = other.hudX(screenWidth);
            float otherY = other.hudY(screenHeight);
            float otherWidth = other.hudWidth(screenWidth);
            float otherHeight = other.hudHeight(screenHeight);
            if (close(y, otherY) && close(height, otherHeight)) {
                left |= close(x, otherX + otherWidth);
                right |= close(x + width, otherX);
            }
            if (close(x, otherX) && close(width, otherWidth)) {
                top |= close(y, otherY + otherHeight);
                bottom |= close(y + height, otherY);
            }
        }
        return new Edges(left, right, top, bottom);
    }

    private static boolean touches(EpsilonHudModule first, EpsilonHudModule second,
                                   float screenWidth, float screenHeight) {
        float x = first.hudX(screenWidth);
        float y = first.hudY(screenHeight);
        float width = first.hudWidth(screenWidth);
        float height = first.hudHeight(screenHeight);
        float otherX = second.hudX(screenWidth);
        float otherY = second.hudY(screenHeight);
        float otherWidth = second.hudWidth(screenWidth);
        float otherHeight = second.hudHeight(screenHeight);
        boolean compactPair = compactReadout(first) && compactReadout(second);
        boolean horizontal = close(y, otherY) && (close(height, otherHeight) || compactPair)
                && (close(x, otherX + otherWidth) || close(x + width, otherX));
        boolean vertical = close(x, otherX) && (close(width, otherWidth) || compactPair)
                && (close(y, otherY + otherHeight) || close(y + height, otherY));
        return horizontal || vertical;
    }

    static boolean compactReadout(EpsilonHudModule module) {
        return module instanceof FPSHUD || module instanceof BPSHUD
                || module instanceof CoordinatesHUD;
    }

    private static boolean sameSpan(float start, float end, float otherStart, float otherEnd) {
        return close(start, otherStart) && close(end - start, otherEnd - otherStart);
    }

    private static boolean close(float first, float second) {
        return Math.abs(first - second) <= TOUCH_TOLERANCE;
    }

    public record FusionStyle(boolean background, int backgroundColor,
                              boolean blur, float blurStrength,
                              boolean shadow, float shadowStrength,
                              boolean border, float radius, float borderWidth,
                              HudRenderUtil.BorderMode borderMode,
                              int borderColor, int borderStart, int borderEnd) {
    }

    record FusionSelection(EpsilonHudModule owner, List<EpsilonHudModule> members,
                           List<FusionCell> cells, Rect bounds, boolean compactStrip) {
        boolean fused() {
            return members.size() > 1;
        }

        Rect cellBounds(EpsilonHudModule module) {
            for (FusionCell cell : cells) {
                if (cell.module() == module) return cell.bounds();
            }
            return bounds;
        }
    }

    private record FusionCell(EpsilonHudModule module, Rect bounds) {
    }

    private record Group(List<EpsilonHudModule> members, EpsilonHudModule owner) {
    }

    record Edges(boolean left, boolean right, boolean top, boolean bottom) {
        static final Edges NONE = new Edges(false, false, false, false);

        boolean any() {
            return left || right || top || bottom;
        }
    }
}
