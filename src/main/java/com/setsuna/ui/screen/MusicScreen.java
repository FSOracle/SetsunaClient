package com.setsuna.ui.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.setsuna.module.modules.player.NetEaseMusicModule;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import tritium.ncm.OptionsUtil;
import tritium.ncm.api.CloudMusicApi;
import tritium.ncm.music.AudioPlayer;
import tritium.ncm.music.CloudMusic;
import tritium.ncm.music.NcmLyrics;
import tritium.ncm.music.MusicProvider;
import tritium.ncm.music.QqMusic;
import tritium.ncm.music.QRCodeGenerator;
import tritium.ncm.music.dto.Music;
import tritium.ncm.music.dto.PlayList;
import tritium.utils.json.JsonUtils;
import top.fpsmaster.music.QrCode;
import top.fpsmaster.music.QrLoginState;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Full-screen music workspace shared by the NetEase and QQ providers. */
public final class MusicScreen extends AbstractSkijaScreen {

    private static final int TITLE_HEIGHT = 36;
    private static final int PLAYER_HEIGHT = 48;
    private static final int ROW_HEIGHT = 34;

    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0x8FFFFFFF;
    private static final int OUTLINE = 0x1FFFFFFF;
    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath(
            "setsuna", "textures/hud/watermark_logo.png");
    private static final Paint COVER_PAINT = new Paint().setAntiAlias(true).setDither(true);
    private static final Paint BACKGROUND_PAINT = new Paint().setAntiAlias(true).setDither(true);

    private static final String CONTROL_PREVIOUS = "H";
    private static final String CONTROL_PAUSE = "A";
    private static final String CONTROL_PLAY = "B";
    private static final String CONTROL_NEXT = "E";
    private static final String MUSIC_HOME = "A";
    private static final String MUSIC_SEARCH = "K";
    private static final String MUSIC_LIKE = "C";
    private static final String MUSIC_DAILY = "D";
    private static final String MUSIC_VOLUME_LOW = "I";
    private static final String MUSIC_VOLUME_HIGH = "J";
    private static final String CLIENT_REFRESH = "D";
    private static final String CLIENT_CLOSE = "p";

    private static final HttpClient COVER_HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final Screen parent;
    private final UiControls.TextInput search = new UiControls.TextInput(128);
    private EditBox searchInput;
    private final List<ClickRegion> clickRegions = new ArrayList<>();
    private final Map<String, Image> coverImages = new HashMap<>();
    private final Map<String, Float> interactionAnimations = new HashMap<>();
    private final Set<String> loadingCovers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> coverRetryAt = new ConcurrentHashMap<>();

    private Page page = Page.HOME;
    private MusicProvider provider = MusicProvider.NETEASE;
    private PlayList selectedPlaylist;
    private List<PlayList> recommendations = List.of();
    private List<Music> homeSongs = List.of();
    private List<Music> visibleSongs = List.of();
    private List<Music> playlistSongs = List.of();
    private float contentScroll;
    private boolean searching;
    private boolean accountBusy;
    private boolean playQueued;
    private boolean loadingPage;
    private boolean loadingHome;
    private boolean draggingProgress;
    private boolean draggingVolume;
    private float pendingSeek = -1;
    private int searchGeneration;
    private int pageRequestGeneration;
    private String status = "Ready";
    private boolean statusError;
    private boolean disposed;
    private volatile CloudMusic.QrLoginState qrLoginState = CloudMusic.QrLoginState.IDLE;
    private Thread qrLoginThread;
    private Image qrImage;
    private String qrImageAddress = "";
    private boolean nowPlayingView;
    private float nowPlayingTransition;
    private long lastFrameNanos = System.nanoTime();
    private float preferredVolume = 0.25F;
    private boolean preferredVolumeChanged;
    private AudioPlayer volumePlayer;
    private Image customBackground;
    private final float[] spectrumBars = new float[56];
    private float panelTransition;
    private float pageTransition = 1.0F;
    private float navHighlightY = Float.NaN;
    private float frameDelta;
    private boolean closing;
    private boolean closeReady;

    public MusicScreen(Screen parent) {
        super(Component.literal("Music"));
        this.parent = parent;
        search.setPlaceholder("Search...");
    }

    @Override
    protected int backdropColor() {
        return setAlpha(backgroundColor(), 184);
    }

    @Override
    protected void init() {
        disposed = false;
        boolean restoreSearchFocus = search.isFocused();
        searchInput = new EditBox(font, 0, 0, 1, 1, Component.literal("Music search"));
        searchInput.setMaxLength(128);
        searchInput.setBordered(false);
        searchInput.setCanLoseFocus(true);
        searchInput.setValue(search.text());
        searchInput.setResponder(search::setText);
        searchInput.active = restoreSearchFocus;
        addWidget(searchInput);
        if (restoreSearchFocus) setFocused(searchInput);
        if (customBackground != null) {
            customBackground.close();
        }
        customBackground = MusicBackgroundSettings.loadImage();
        Layout layout = layout();
        updateInputBounds(layout);
        initializeProvider();
    }

    @Override
    protected void drawScreen(Canvas canvas) {
        SkijaUi.withFont(SkijaUi.CLIENT_FONT, () -> drawMusicScreen(canvas));
    }

    private void drawMusicScreen(Canvas canvas) {
        updateViewTransition();
        Layout libraryLayout = libraryLayout();
        updateInputBounds(libraryLayout);
        clickRegions.clear();

        float panelEase = easeOutCubic(panelTransition);
        float panelScale = 0.955F + panelEase * 0.045F;
        int outerSave = canvas.save();
        try {
            canvas.translate(width * 0.5F, height * 0.5F);
            canvas.scale(panelScale, panelScale);
            canvas.translate(-width * 0.5F, -height * 0.5F);
            drawLibrary(canvas, libraryLayout);
            if (nowPlayingTransition > 0.001F && CloudMusic.currentlyPlaying != null) {
                clickRegions.clear();
                float eased = easeOutCubic(nowPlayingTransition);
                Layout nowLayout = nowPlayingLayout();
                int playingSave = canvas.save();
                try {
                    canvas.translate(0, (1.0F - eased) * Math.max(1, height));
                    SkijaUi.dropShadowRounded(canvas, nowLayout.frame.x(), nowLayout.frame.y(),
                            nowLayout.frame.width(), nowLayout.frame.height(), 6, 5, 18, 0xB0000000);
                    canvas.clipRRect(RRect.makeXYWH(nowLayout.frame.x(), nowLayout.frame.y(),
                            nowLayout.frame.width(), nowLayout.frame.height(), 6));
                    drawNowPlaying(canvas, nowLayout);
                } finally {
                    canvas.restoreToCount(playingSave);
                }
                if (!nowPlayingView || nowPlayingTransition < 0.92F) {
                    clickRegions.clear();
                }
            }
        } finally {
            canvas.restoreToCount(outerSave);
        }
        if (panelTransition < 0.92F || closing) {
            clickRegions.clear();
        }
    }

    private void drawLibrary(Canvas canvas, Layout layout) {
        drawWindow(canvas, layout);
        int frameSave = canvas.save();
        try {
            canvas.clipRRect(RRect.makeXYWH(layout.frame.x(), layout.frame.y(), layout.frame.width(),
                    layout.frame.height(), 6));
            drawSidebar(canvas, layout);
            drawTopBar(canvas, layout);
            int contentSave = canvas.save();
            try {
                canvas.clipRect(Rect.makeXYWH(layout.content.x(), layout.content.y(), layout.content.width(),
                        layout.content.height()));
                float pageEase = easeOutCubic(pageTransition);
                canvas.translate((1.0F - pageEase) * 12.0F, 0);
                if (!hasAccount()) {
                    drawLogin(canvas, layout);
                } else {
                    drawContent(canvas, layout);
                }
            } finally {
                canvas.restoreToCount(contentSave);
            }
            drawPlayer(canvas, layout);
        } finally {
            canvas.restoreToCount(frameSave);
        }
    }

    private void drawWindow(Canvas canvas, Layout layout) {
        UiControls.Box frame = layout.frame;
        SkijaUi.dropShadowRounded(canvas, frame.x(), frame.y(), frame.width(), frame.height(), 6,
                5, 18, 0xB0000000);
        int save = canvas.save();
        try {
            canvas.clipRRect(RRect.makeXYWH(frame.x(), frame.y(), frame.width(), frame.height(), 6));
            SkijaUi.rounded(canvas, frame.x(), frame.y(), frame.width(), frame.height(), 6,
                    backgroundColor());
            if (customBackground != null) {
                drawImageCover(canvas, customBackground, frame,
                        NetEaseMusicModule.INSTANCE.backgroundOpacity.get() * 255 / 100);
                SkijaUi.rounded(canvas, frame.x(), frame.y(), frame.width(), frame.height(), 6,
                        setAlpha(backgroundColor(), 92));
            }
            SkijaUi.fill(canvas, layout.main.x(), layout.main.y(), layout.main.width(), layout.main.height(),
                    customBackground == null ? surfaceColor() : setAlpha(surfaceColor(), 178));
            SkijaUi.line(canvas, layout.sidebar.x() + layout.sidebar.width(), frame.y(),
                    layout.sidebar.x() + layout.sidebar.width(), frame.y() + frame.height(), 0.6F, OUTLINE);
            SkijaUi.gradient(canvas, frame.x(), frame.y(), frame.width(), 2,
                    accent(), secondary(), false, 0);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private void drawTopBar(Canvas canvas, Layout layout) {
        SkijaUi.fill(canvas, layout.main.x(), layout.frame.y(), layout.main.width(), TITLE_HEIGHT,
                customBackground == null ? backgroundColor() : setAlpha(backgroundColor(), 205));
        SkijaUi.line(canvas, layout.main.x(), layout.frame.y() + TITLE_HEIGHT,
                layout.frame.x() + layout.frame.width(), layout.frame.y() + TITLE_HEIGHT, 0.6F, OUTLINE);
        search.draw(canvas, mouseX, mouseY, accent(), setAlpha(accent(), 24), 0x26FFFFFF);
        drawProviderSelector(canvas, layout);
        drawSmallIconButton(canvas, layout.back, CLIENT_CLOSE, SkijaUi.IconSet.CLIENT_ICONS,
                true, false, 8);
        clickRegions.add(new ClickRegion(Action.BACK, null, layout.back));
    }

    private void drawProviderSelector(Canvas canvas, Layout layout) {
        float left = layout.search.x() + layout.search.width() + 8;
        float available = Math.max(0, layout.back.x() - left - 7);
        float gap = 3;
        float buttonWidth = Math.min(58, Math.max(34, (available - gap) * 0.5F));
        if (available < 71) return;
        UiControls.Box netease = new UiControls.Box(left, layout.frame.y() + 9, buttonWidth, 18);
        UiControls.Box qq = new UiControls.Box(left + buttonWidth + gap, layout.frame.y() + 9, buttonWidth, 18);
        drawProviderButton(canvas, netease, MusicProvider.NETEASE, buttonWidth < 48 ? "NE" : "NetEase");
        drawProviderButton(canvas, qq, MusicProvider.QQ, "QQ");
    }

    private void drawProviderButton(Canvas canvas, UiControls.Box box, MusicProvider target, String label) {
        boolean selected = provider == target;
        boolean hovered = box.contains(mouseX, mouseY);
        SkijaUi.rounded(canvas, box.x(), box.y(), box.width(), box.height(), 3,
                selected ? setAlpha(accent(), 48) : hovered ? 0x18FFFFFF : 0x0CFFFFFF);
        UiControls.centeredText(canvas, label, box, selected ? accent() : TEXT_MUTED, false);
        clickRegions.add(new ClickRegion(Action.SOURCE, target, box));
    }

    private void drawSidebar(Canvas canvas, Layout layout) {
        SkijaUi.fill(canvas, layout.sidebar.x(), layout.frame.y(), layout.sidebar.width(),
                layout.frame.height(), customBackground == null ? sidebarColor() : setAlpha(sidebarColor(), 218));

        drawSetsunaLogo(canvas, layout.sidebar.x() + (layout.sidebar.width() - 21) * 0.5F,
                layout.frame.y() + 8, 21);

        String profile = profileName();
        UiControls.Box avatar = layout.avatar;
        SkijaUi.rounded(canvas, avatar.x(), avatar.y(), avatar.width(), avatar.height(), avatar.width() * 0.5F,
                !hasAccount() ? 0x22FFFFFF : setAlpha(accent(), 66));
        UiControls.centeredText(canvas, firstLetter(profile), avatar, !hasAccount() ? TEXT_MUTED : TEXT,
                true);
        if (!layout.compact) {
            UiControls.centeredText(canvas, UiControls.ellipsize(profile, layout.sidebar.width() - 8),
                    new UiControls.Box(layout.sidebar.x() + 4, avatar.y() + avatar.height() + 3,
                            layout.sidebar.width() - 8, 12), TEXT_MUTED, false);
        }

        float navY = layout.navY;
        int activeIndex = switch (page) {
            case SEARCH -> 1;
            case LIKED -> 2;
            case DAILY -> 3;
            case HOME, PLAYLIST -> 0;
        };
        float navTargetY = navY + activeIndex * 24;
        if (!Float.isFinite(navHighlightY)) {
            navHighlightY = navTargetY;
        }
        navHighlightY += (navTargetY - navHighlightY) * Math.min(1.0F, frameDelta * 10.0F);
        SkijaUi.rounded(canvas, layout.sidebar.x() + 5, navHighlightY,
                layout.sidebar.width() - 10, 20, 3, setAlpha(accent(), 42));
        drawNav(canvas, layout, MUSIC_HOME, "Home", Page.HOME, navY);
        drawNav(canvas, layout, MUSIC_SEARCH, "Search", Page.SEARCH, navY + 24);
        drawNav(canvas, layout, MUSIC_LIKE, "Like", Page.LIKED, navY + 48);
        drawNav(canvas, layout, MUSIC_DAILY, provider == MusicProvider.QQ ? "Top" : "Daily",
                Page.DAILY, navY + 72);

        drawSmallIconButton(canvas, layout.reload, CLIENT_REFRESH, SkijaUi.IconSet.CLIENT_ICONS,
                !accountBusy && !playQueued, false, 9);
        drawSmallIconButton(canvas, layout.logout, CLIENT_CLOSE, SkijaUi.IconSet.CLIENT_ICONS,
                hasAccount() && !accountBusy && !playQueued, true, 8);
        clickRegions.add(new ClickRegion(Action.RELOAD, null, layout.reload));
        clickRegions.add(new ClickRegion(Action.LOGOUT, null, layout.logout));
    }

    private void drawNav(Canvas canvas, Layout layout, String glyph, String title, Page target, float y) {
        UiControls.Box box = new UiControls.Box(layout.sidebar.x() + 6, y, layout.sidebar.width() - 12, 20);
        boolean active = page == target || target == Page.HOME && page == Page.PLAYLIST;
        boolean hovered = box.contains(mouseX, mouseY);
        float hover = animateInteraction("nav:" + title, hovered);
        int fill = active ? 0x00000000 : withAlpha(0x24FFFFFF, hover);
        SkijaUi.rounded(canvas, box.x(), box.y(), box.width(), box.height(), 3, fill);
        float iconSize = 8;
        float textSize = 7;
        float iconWidth = SkijaUi.iconWidth(glyph, iconSize, SkijaUi.IconSet.TRITIUM_MUSIC);
        float textWidth = SkijaUi.textWidth(title, textSize);
        float gap = 4;
        float startX = box.x() + Math.max(3, (box.width() - iconWidth - gap - textWidth) * 0.5F);
        int color = active ? accent() : hovered ? TEXT : 0xA8FFFFFF;
        SkijaUi.icon(canvas, glyph, startX, box.y(), box.height(), color, iconSize,
                SkijaUi.IconSet.TRITIUM_MUSIC);
        SkijaUi.text(canvas, title, startX + iconWidth + gap, box.y(), box.height(), color, textSize);
        clickRegions.add(new ClickRegion(Action.PAGE, target, box));
    }

    private void drawLogin(Canvas canvas, Layout layout) {
        syncQrImage();
        UiControls.Box content = layout.content;
        float panelWidth = Math.min(286, Math.max(120, content.width() - 30));
        float x = content.x() + (content.width() - panelWidth) * 0.5F;
        SkijaUi.boldText(canvas, provider.displayName() + " QR Login", x,
                layout.qrCode.y() - 23, 18, TEXT, 12);

        UiControls.Box qr = layout.qrCode;
        SkijaUi.rounded(canvas, qr.x() - 2, qr.y() - 2, qr.width() + 4, qr.height() + 4, 4,
                0x34FFFFFF);
        SkijaUi.rounded(canvas, qr.x(), qr.y(), qr.width(), qr.height(), 3, 0xFFFFFFFF);
        if (qrImage != null) {
            canvas.drawImageRect(qrImage, Rect.makeXYWH(qr.x(), qr.y(), qr.width(), qr.height()));
        } else {
            UiControls.centeredText(canvas, "...", qr, 0xFF101718, true);
        }

        drawMusicButton(canvas, layout.qrLogin,
                accountBusy ? "Refresh QR code" : "Generate QR code", !playQueued, true);
        String loginStatus = qrLoginStatus();
        SkijaUi.text(canvas, UiControls.ellipsize(loginStatus, panelWidth), x,
                layout.qrLogin.y() + 25, 12, statusError ? UiTheme.DANGER : TEXT_MUTED, 8);
        clickRegions.add(new ClickRegion(Action.QR_LOGIN, null, layout.qrLogin));
    }

    private void drawContent(Canvas canvas, Layout layout) {
        switch (page) {
            case HOME -> drawHome(canvas, layout);
            case PLAYLIST -> drawPlaylist(canvas, layout);
            case SEARCH, LIKED, DAILY -> drawSongPage(canvas, layout);
        }
    }

    private void drawHome(Canvas canvas, Layout layout) {
        List<PlayList> playlists = homePlaylists();
        if (homeSongs.isEmpty()) {
            primeHomeSongs();
        }

        UiControls.Box content = layout.content;
        float padding = 14;
        float heroHeight = UiControls.clamp(content.height() * 0.34F, 72, 126);
        float cardGap = 8;
        int recCols = Math.max(2, Math.min(5, (int) ((content.width() - padding * 2) / 76)));
        float recSize = Math.max(42, (content.width() - padding * 2 - cardGap * (recCols - 1)) / recCols);
        int songCount = Math.min(homeSongs.size(), 18);
        float totalHeight = heroHeight + recSize + 82 + songCount * ROW_HEIGHT;
        clampScroll(content.height(), totalHeight);
        float y = content.y() + contentScroll;

        UiControls.Box hero = new UiControls.Box(content.x() + padding, y + 10,
                content.width() - padding * 2, heroHeight - 10);
        SkijaUi.rounded(canvas, hero.x(), hero.y(), hero.width(), hero.height(), 3, 0xFF121B1C);
        Music featured = homeSongs.isEmpty() ? null : homeSongs.getFirst();
        if (featured != null) {
            float artWidth = Math.min(hero.width() * 0.43F, hero.height() * 1.55F);
            UiControls.Box art = new UiControls.Box(hero.x() + hero.width() - artWidth, hero.y(),
                    artWidth, hero.height());
            drawCover(canvas, "music:" + featured.cacheKey(), featured.getCoverUrl(512), art, 3);
            SkijaUi.gradient(canvas, art.x() - 1, art.y(), art.width() * 0.58F, art.height(),
                    0xFF121B1C, 0x00121B1C, false, 0);
            SkijaUi.text(canvas, "SETSUNA SELECTION", hero.x() + 14, hero.y() + 12,
                    10, accent(), 6);
            SkijaUi.boldText(canvas, UiControls.ellipsize(featured.getName(), hero.width() * 0.5F),
                    hero.x() + 14, hero.y() + 28, 24, TEXT, 15);
            SkijaUi.text(canvas, UiControls.ellipsize(featured.getArtistsName(), hero.width() * 0.48F),
                    hero.x() + 14, hero.y() + 51, 13, TEXT_MUTED, 7);
            UiControls.Box heroPlay = new UiControls.Box(hero.x() + 14,
                    hero.y() + hero.height() - 28, 58, 18);
            drawMusicButton(canvas, heroPlay, "Play now", !playQueued, true);
            clickRegions.add(new ClickRegion(Action.PLAY_SONG, new SongClick(homeSongs, 0), heroPlay));
        } else {
            SkijaUi.boldText(canvas, loadingHome ? "Loading your music" : "Your music, in one place",
                    hero.x() + 14, hero.y() + 25, 26, TEXT, 15);
            SkijaUi.text(canvas, UiControls.ellipsize(displayStatus(), hero.width() - 28), hero.x() + 14,
                    hero.y() + 55, 14, TEXT_MUTED, 7);
        }

        float recTitleY = y + heroHeight + 9;
        SkijaUi.boldText(canvas, "For you", content.x() + padding, recTitleY, 18, TEXT, 11);
        SkijaUi.text(canvas, "CURATED PLAYLISTS", content.x() + padding + 46, recTitleY + 4,
                10, 0x58FFFFFF, 5.5F);
        float recY = recTitleY + 21;
        int recLimit = Math.min(playlists.size(), recCols);
        for (int i = 0; i < recLimit; i++) {
            PlayList playlist = playlists.get(i);
            float cardX = content.x() + padding + i * (recSize + cardGap);
            UiControls.Box cover = new UiControls.Box(cardX, recY, recSize, recSize * 0.72F);
            if (!intersects(cover, content)) {
                continue;
            }
            drawCover(canvas, "playlist:" + playlist.cacheKey(), playlist.getCoverUrl(), cover, 3);
            if (cover.contains(mouseX, mouseY)) {
                SkijaUi.fill(canvas, cover.x(), cover.y(), cover.width(), cover.height(), 0x20000000);
                drawPlayDisc(canvas, cover.x() + cover.width() - 11, cover.y() + cover.height() - 11,
                        8, TEXT, 0xD9080D0E);
            }
            SkijaUi.text(canvas, UiControls.ellipsize(playlist.getName(), recSize), cardX,
                    cover.y() + cover.height() + 3, 12, TEXT, 7);
            clickRegions.add(new ClickRegion(Action.PLAYLIST, playlist,
                    new UiControls.Box(cardX, recY, recSize, cover.height() + 15)));
        }

        float songsTitleY = recY + recSize * 0.72F + 24;
        SkijaUi.boldText(canvas, "Recently queued", content.x() + padding, songsTitleY, 17, TEXT, 10);
        drawSongs(canvas, content.x() + padding, songsTitleY + 20, content.width() - padding * 2,
                homeSongs.subList(0, songCount), content);
    }

    private void drawPlaylist(Canvas canvas, Layout layout) {
        if (selectedPlaylist == null) {
            page = Page.HOME;
            drawHome(canvas, layout);
            return;
        }
        if (loadingPage && playlistSongs.isEmpty() && selectedPlaylist.musics != null
                && !selectedPlaylist.musics.isEmpty()) {
            playlistSongs = safeCopy(selectedPlaylist.musics);
            loadingPage = false;
            setStatus(playlistSongs.size() + " songs", false);
        } else if (loadingPage && selectedPlaylist.musics != null && selectedPlaylist.musics.isEmpty()
                && (selectedPlaylist.musicsLoaded || !selectedPlaylist.musicsQueried)) {
            loadingPage = false;
            setStatus(selectedPlaylist.musicsLoaded ? "No songs" : "Playlist load failed",
                    !selectedPlaylist.musicsLoaded);
        }
        UiControls.Box content = layout.content;
        float totalHeight = 104 + playlistSongs.size() * ROW_HEIGHT;
        clampScroll(content.height(), totalHeight);
        float y = content.y() + contentScroll;
        UiControls.Box cover = new UiControls.Box(content.x() + 12, y + 12, 72, 72);
        SkijaUi.rounded(canvas, cover.x(), cover.y(), cover.width(), cover.height(), 6, cardColor());
        drawCover(canvas, "playlist:" + selectedPlaylist.cacheKey(), selectedPlaylist.getCoverUrl(), cover, 6);
        float textX = cover.x() + cover.width() + 12;
        SkijaUi.boldText(canvas, UiControls.ellipsize(selectedPlaylist.getName(), content.width() - 108),
                textX, y + 19, 19, TEXT, 12);
        SkijaUi.text(canvas, playlistInfo(selectedPlaylist), textX, y + 40, 12, TEXT_MUTED, 8);
        UiControls.Box playAll = new UiControls.Box(textX, y + 62, 50, 20);
        UiControls.Box shuffle = new UiControls.Box(textX + 56, y + 62, 62, 20);
        drawMusicButton(canvas, playAll, "Play", !playlistSongs.isEmpty() && !playQueued, true);
        drawMusicButton(canvas, shuffle, "Shuffle", !playlistSongs.isEmpty() && !playQueued, false);
        clickRegions.add(new ClickRegion(Action.PLAY_ALL, null, playAll));
        clickRegions.add(new ClickRegion(Action.SHUFFLE, null, shuffle));
        drawSongs(canvas, content.x() + 10, y + 98, content.width() - 20, playlistSongs, content);
    }

    private void drawSongPage(Canvas canvas, Layout layout) {
        UiControls.Box content = layout.content;
        float totalHeight = 48 + visibleSongs.size() * ROW_HEIGHT;
        clampScroll(content.height(), totalHeight);
        float y = content.y() + contentScroll;
        SkijaUi.boldText(canvas, pageTitle(), content.x() + 12, y + 10, 18, TEXT, 11);
        int statusColor = statusError ? UiTheme.DANGER : TEXT_MUTED;
        SkijaUi.text(canvas, UiControls.ellipsize(loadingPage ? "Loading..." : displayStatus(),
                        content.width() - 24), content.x() + 12, y + 27, 12, statusColor, 7);
        drawSongs(canvas, content.x() + 10, y + 47, content.width() - 20, visibleSongs, content);
    }

    private void drawSongs(Canvas canvas, float x, float y, float width, List<Music> songs,
                           UiControls.Box viewport) {
        if (songs.isEmpty()) {
            UiControls.centeredText(canvas, loadingPage ? "Loading..." : "No songs",
                    new UiControls.Box(x, Math.max(y, viewport.y() + 48), width,
                            Math.max(24, viewport.y() + viewport.height() - Math.max(y, viewport.y() + 48))),
                    TEXT_MUTED, false);
            return;
        }
        for (int i = 0; i < songs.size(); i++) {
            Music music = songs.get(i);
            float rowY = y + i * ROW_HEIGHT;
            if (rowY + 30 < viewport.y() || rowY > viewport.y() + viewport.height()) {
                continue;
            }
            UiControls.Box row = new UiControls.Box(x, rowY, width, 30);
            boolean current = music.equals(CloudMusic.currentlyPlaying);
            int fill = current ? setAlpha(accent(), 36)
                    : row.contains(mouseX, mouseY) ? 0x14FFFFFF : 0x00000000;
            SkijaUi.rounded(canvas, row.x(), row.y(), row.width(), row.height(), 3, fill);
            UiControls.Box cover = new UiControls.Box(row.x() + 5, row.y() + 4, 22, 22);
            drawCover(canvas, "music:" + music.cacheKey(), music.getCoverUrl(64), cover, 2);
            SkijaUi.text(canvas, String.format("%02d", i + 1), row.x() + 32, row.y() + 9,
                    12, current ? accent() : 0x54FFFFFF, 5.5F);
            float durationWidth = 40;
            float titleWidth = row.width() > 290 ? row.width() * 0.58F - 55 : row.width() - 102;
            SkijaUi.text(canvas, UiControls.ellipsize(music.getName(), titleWidth), row.x() + 48,
                    row.y() + 4, 13, TEXT, 8);
            SkijaUi.text(canvas, UiControls.ellipsize(music.getArtistsName(), titleWidth), row.x() + 48,
                    row.y() + 16, 11, TEXT_MUTED, 7);
            float durationX = row.x() + row.width() - durationWidth;
            if (row.width() > 290) {
                String quality = qualityLabel(music);
                float badgeWidth = SkijaUi.textWidth(quality, 5.5F) + 8;
                float badgeX = durationX - badgeWidth - 8;
                float albumX = row.x() + row.width() * 0.58F;
                float albumWidth = Math.max(0, badgeX - albumX - 7);
                if (music.getAlbum() != null && albumWidth > 18) {
                    SkijaUi.text(canvas, UiControls.ellipsize(music.getAlbum().getName(), albumWidth),
                            albumX, row.y() + 9, 12, 0x62FFFFFF, 6);
                }
                SkijaUi.rounded(canvas, badgeX, row.y() + 8, badgeWidth, 13, 2,
                        setAlpha(secondary(), 31));
                UiControls.centeredText(canvas, quality,
                        new UiControls.Box(badgeX, row.y() + 8, badgeWidth, 13), secondary(), false);
            }
            SkijaUi.text(canvas, formatDuration(music.getDuration()), durationX,
                    row.y() + 9, 12, TEXT_MUTED, 7);
            clickRegions.add(new ClickRegion(Action.PLAY_SONG, new SongClick(songs, i), row));
        }
    }

    private void drawPlayer(Canvas canvas, Layout layout) {
        UiControls.Box playerBox = layout.player;
        SkijaUi.fill(canvas, playerBox.x(), playerBox.y(), playerBox.width(), playerBox.height(), 0xFA090E0F);
        SkijaUi.line(canvas, playerBox.x(), playerBox.y(), playerBox.x() + playerBox.width(),
                playerBox.y(), 0.6F, OUTLINE);
        Music current = CloudMusic.currentlyPlaying;
        AudioPlayer player = accountBusy ? null : CloudMusic.player;
        float progress = playbackProgress(player);
        UiControls.Box progressBox = layout.progress;
        boolean progressHot = progressBox.contains(mouseX, mouseY) || draggingProgress;
        SkijaUi.rounded(canvas, progressBox.x(), progressBox.y(), progressBox.width(), progressBox.height(), 2,
                progressHot ? 0x48FFFFFF : 0x20FFFFFF);
        SkijaUi.rounded(canvas, progressBox.x(), progressBox.y(), progressBox.width() * progress,
                progressBox.height(), 2, accent());
        clickRegions.add(new ClickRegion(Action.PROGRESS, null,
                new UiControls.Box(progressBox.x(), progressBox.y() - 4, progressBox.width(), 11)));

        UiControls.Box cover = layout.playerCover;
        SkijaUi.rounded(canvas, cover.x(), cover.y(), cover.width(), cover.height(), 2, cardColor());
        if (current != null) {
            drawCover(canvas, "music:" + current.cacheKey(), current.getCoverUrl(96), cover, 2);
            clickRegions.add(new ClickRegion(Action.NOW_PLAYING, null,
                    new UiControls.Box(cover.x(), cover.y(), layout.trackTextWidth + cover.width() + 8,
                            cover.height())));
        }
        SkijaUi.text(canvas, UiControls.ellipsize(current == null ? "Not Playing" : current.getName(),
                        layout.trackTextWidth), cover.x() + cover.width() + 6, playerBox.y() + 7,
                12, TEXT, 7);
        SkijaUi.text(canvas, UiControls.ellipsize(current == null ? "None" : current.getArtistsName(),
                        layout.trackTextWidth), cover.x() + cover.width() + 6, playerBox.y() + 19,
                11, TEXT_MUTED, 6);

        boolean playerReady = player != null && !playQueued;
        drawPlayerButton(canvas, layout.previous, CONTROL_PREVIOUS, playerReady);
        drawPlayerButton(canvas, layout.pause,
                player != null && !player.isPausing() ? CONTROL_PAUSE : CONTROL_PLAY, playerReady);
        drawPlayerButton(canvas, layout.next, CONTROL_NEXT, playerReady);
        clickRegions.add(new ClickRegion(Action.PREVIOUS, null, layout.previous));
        clickRegions.add(new ClickRegion(Action.PAUSE, null, layout.pause));
        clickRegions.add(new ClickRegion(Action.NEXT, null, layout.next));

        if (current != null) {
            String time = formatDuration(currentTime(player)) + " / " + formatDuration(totalTime(current, player));
            if (layout.time.width() > 2) {
                SkijaUi.text(canvas, UiControls.ellipsize(time, layout.time.width()), layout.time.x(),
                        layout.time.y(), layout.time.height(), 0xCCFFFFFF, 6);
            }
            drawLikeButton(canvas, layout.like, isLiked(current));
            clickRegions.add(new ClickRegion(Action.LIKE, current, layout.like));
        }

        float volume = currentVolume(player);
        UiControls.Box volumeBox = layout.volume;
        SkijaUi.rounded(canvas, volumeBox.x(), volumeBox.y(), volumeBox.width(), volumeBox.height(), 2,
                0x38FFFFFF);
        SkijaUi.rounded(canvas, volumeBox.x(), volumeBox.y(), volumeBox.width() * volume,
                volumeBox.height(), 2, accent());
        drawVolumeKnob(canvas, volumeBox, volume);
        UiControls.Box volumeIcon = new UiControls.Box(volumeBox.x() - 16, volumeBox.y() - 6, 13, 14);
        drawCenteredIcon(canvas, volume <= 0.01F ? MUSIC_VOLUME_LOW : MUSIC_VOLUME_HIGH, volumeIcon,
                0xB8FFFFFF, 9, SkijaUi.IconSet.TRITIUM_MUSIC);
        clickRegions.add(new ClickRegion(Action.VOLUME, null,
                new UiControls.Box(volumeIcon.x(), volumeBox.y() - 7,
                        volumeBox.x() + volumeBox.width() - volumeIcon.x() + 3, 17)));
    }

    private void drawNowPlaying(Canvas canvas, Layout layout) {
        UiControls.Box frame = layout.frame;
        SkijaUi.fill(canvas, frame.x(), frame.y(), frame.width(), frame.height(), backgroundColor());
        if (customBackground != null) {
            drawImageCover(canvas, customBackground, frame,
                    NetEaseMusicModule.INSTANCE.backgroundOpacity.get() * 255 / 100);
        }
        SkijaUi.gradient(canvas, frame.x(), frame.y(), frame.width(), frame.height(),
                setAlpha(mixColor(surfaceColor(), accent(), 0.12F), customBackground == null ? 255 : 205),
                setAlpha(backgroundColor(), customBackground == null ? 255 : 220), false, 0);

        Music current = CloudMusic.currentlyPlaying;
        AudioPlayer player = accountBusy ? null : CloudMusic.player;
        if (current == null) {
            nowPlayingView = false;
            return;
        }
        NcmLyrics.ensureLoaded(current);

        float pad = layout.compact ? 10 : 18;
        float headerHeight = 34;
        float queueWidth = layout.compact ? 0 : UiControls.clamp(frame.width() * 0.245F, 112, 190);
        float queueX = frame.x() + frame.width() - queueWidth;
        float leftWidth = UiControls.clamp(frame.width() * (layout.compact ? 0.32F : 0.29F), 92, 215);
        float coverSize = Math.min(leftWidth - pad * 1.4F,
                Math.max(72, frame.height() - headerHeight - 76));
        coverSize = Math.min(coverSize, frame.height() * 0.57F);
        float coverX = frame.x() + pad;
        float coverY = frame.y() + headerHeight + Math.max(10,
                (frame.height() - headerHeight - 48 - coverSize) * 0.43F);
        UiControls.Box cover = new UiControls.Box(coverX, coverY, coverSize, coverSize);

        drawNowPlayingHeader(canvas, layout, current);
        SkijaUi.dropShadowRounded(canvas, cover.x(), cover.y(), cover.width(), cover.height(), 2,
                4, 14, 0x98000000);
        drawCover(canvas, "music-large:" + current.getId(), current.getCoverUrl(1024), cover, 2);
        SkijaUi.fill(canvas, cover.x(), cover.y() + cover.height() - 19, cover.width(), 19, 0xC4080D0E);
        SkijaUi.text(canvas, "SETSUNA RECORDS", cover.x() + 6, cover.y() + cover.height() - 13,
                10, 0xBFFFFFFF, 5);
        String albumCode = String.format("ST - %02d", Math.max(1, CloudMusic.curIdx + 1));
        SkijaUi.text(canvas, albumCode,
                cover.x() + cover.width() - SkijaUi.textWidth(albumCode, 5) - 6,
                cover.y() + cover.height() - 13, 10, 0xBFFFFFFF, 5);
        if (current.getAlbum() != null) {
            SkijaUi.text(canvas, "ALBUM", cover.x(), cover.y() + cover.height() + 9,
                    9, accent(), 5);
            SkijaUi.boldText(canvas, UiControls.ellipsize(current.getAlbum().getName(), cover.width()),
                    cover.x(), cover.y() + cover.height() + 20, 16, TEXT, 9);
        }

        float centerX = cover.x() + cover.width() + (layout.compact ? 12 : 22);
        float centerRight = queueWidth > 0 ? queueX - 18 : frame.x() + frame.width() - pad;
        float centerWidth = Math.max(92, centerRight - centerX);
        drawNowPlayingDetails(canvas, current, player, centerX, coverY, centerWidth, layout);

        if (queueWidth > 0) {
            drawQueue(canvas, current, new UiControls.Box(queueX, frame.y() + headerHeight,
                    queueWidth, frame.height() - headerHeight));
        }
    }

    private void drawNowPlayingHeader(Canvas canvas, Layout layout, Music current) {
        UiControls.Box frame = layout.frame;
        drawSetsunaLogo(canvas, frame.x() + 12, frame.y() + 7, 20);
        String now = "NOW PLAYING";
        SkijaUi.text(canvas, now, frame.x() + (frame.width() - SkijaUi.textWidth(now, 5)) * 0.5F,
                frame.y() + 6, 9, 0x66FFFFFF, 5);
        String radio = UiControls.ellipsize(current.getName() + " / Radio", frame.width() * 0.28F);
        SkijaUi.text(canvas, radio, frame.x() + (frame.width() - SkijaUi.textWidth(radio, 6)) * 0.5F,
                frame.y() + 16, 12, TEXT, 6);
        UiControls.Box close = layout.back;
        boolean hovered = close.contains(mouseX, mouseY);
        SkijaUi.outline(canvas, close.x(), close.y(), close.width(), close.height(), close.height() * 0.5F,
                0.7F, hovered ? accent() : 0x32FFFFFF);
        UiControls.centeredText(canvas, "v", close, hovered ? accent() : TEXT_MUTED, true);
        clickRegions.add(new ClickRegion(Action.LIBRARY, null, close));
        SkijaUi.line(canvas, frame.x(), frame.y() + 34, frame.x() + frame.width(), frame.y() + 34,
                0.6F, OUTLINE);
    }

    private void drawNowPlayingDetails(Canvas canvas, Music current, AudioPlayer player, float x, float y,
                                       float width, Layout layout) {
        int queueSize = CloudMusic.playList == null ? 0 : CloudMusic.playList.size();
        String count = String.format("%02d / %02d", Math.max(1, CloudMusic.curIdx + 1), Math.max(1, queueSize));
        SkijaUi.text(canvas, count, x, y - 2, 11, accent(), 5.5F);
        float titleSize = width < 150 ? 14 : 18;
        SkijaUi.boldText(canvas, UiControls.ellipsize(current.getName(), width - 24), x, y + 12,
                27, TEXT, titleSize);
        SkijaUi.text(canvas, UiControls.ellipsize(current.getArtistsName(), width - 24), x, y + 40,
                14, 0xB8FFFFFF, 7);
        UiControls.Box like = new UiControls.Box(x + width - 22, y + 16, 19, 19);
        drawLikeButton(canvas, like, isLiked(current));
        clickRegions.add(new ClickRegion(Action.LIKE, current, like));

        List<NcmLyrics.Line> lyrics = NcmLyrics.getLines();
        float lyricY = y + 74;
        if (lyrics.isEmpty()) {
            SkijaUi.text(canvas, NcmLyrics.isLoading() ? "Loading lyrics..." : "Instrumental / No lyrics",
                    x, lyricY + 15, 15, 0x58FFFFFF, 8);
        } else {
            int index = Math.max(0, Math.min(lyrics.size() - 1,
                    NcmLyrics.currentIndex(currentTime(player))));
            if (index > 0) {
                drawLyricLine(canvas, lyrics.get(index - 1).text(), x, lyricY, width, 0x38FFFFFF, 6.5F);
            }
            drawLyricLine(canvas, lyrics.get(index).text(), x, lyricY + 19, width, TEXT, 10);
            if (index + 1 < lyrics.size()) {
                drawLyricLine(canvas, lyrics.get(index + 1).text(), x, lyricY + 42, width,
                        0x50FFFFFF, 7);
            }
        }

        float waveformY = layout.progress.y() - 43;
        drawWaveform(canvas, x, waveformY, width, 25, player);
        float currentTime = currentTime(player);
        float totalTime = totalTime(current, player);
        float timeY = layout.progress.y() - 15;
        SkijaUi.text(canvas, formatDuration(currentTime), x, timeY, 10, TEXT_MUTED, 5.5F);
        String remaining = "-" + formatDuration(Math.max(0, totalTime - currentTime));
        SkijaUi.text(canvas, remaining, x + width - SkijaUi.textWidth(remaining, 5.5F),
                timeY, 10, TEXT_MUTED, 5.5F);

        UiControls.Box progress = layout.progress;
        SkijaUi.rounded(canvas, progress.x(), progress.y(), progress.width(), progress.height(), 2,
                0x28FFFFFF);
        SkijaUi.rounded(canvas, progress.x(), progress.y(), progress.width() * playbackProgress(player),
                progress.height(), 2, accent());
        clickRegions.add(new ClickRegion(Action.PROGRESS, null,
                new UiControls.Box(progress.x(), progress.y() - 5, progress.width(), 12)));

        boolean ready = player != null && !playQueued;
        drawPlayerButton(canvas, layout.previous, CONTROL_PREVIOUS, ready);
        int playFill = ready ? accent() : 0x2AFFFFFF;
        SkijaUi.rounded(canvas, layout.pause.x(), layout.pause.y(), layout.pause.width(), layout.pause.height(),
                layout.pause.height() * 0.5F, playFill);
        drawCenteredIcon(canvas, player != null && !player.isPausing() ? CONTROL_PAUSE : CONTROL_PLAY,
                layout.pause, ready ? 0xFF061011 : 0x48FFFFFF, 11, SkijaUi.IconSet.TRITIUM_CONTROLS);
        drawPlayerButton(canvas, layout.next, CONTROL_NEXT, ready);
        clickRegions.add(new ClickRegion(Action.PREVIOUS, null, layout.previous));
        clickRegions.add(new ClickRegion(Action.PAUSE, null, layout.pause));
        clickRegions.add(new ClickRegion(Action.NEXT, null, layout.next));

        UiControls.Box volume = layout.volume;
        SkijaUi.rounded(canvas, volume.x(), volume.y(), volume.width(), volume.height(), 2, 0x28FFFFFF);
        SkijaUi.rounded(canvas, volume.x(), volume.y(), volume.width() * currentVolume(player),
                volume.height(), 2, accent());
        drawVolumeKnob(canvas, volume, currentVolume(player));
        clickRegions.add(new ClickRegion(Action.VOLUME, null,
                new UiControls.Box(volume.x() - 7, volume.y() - 7, volume.width() + 14, 17)));
    }

    private void drawQueue(Canvas canvas, Music current, UiControls.Box queue) {
        SkijaUi.fill(canvas, queue.x(), queue.y(), queue.width(), queue.height(), 0x80070B0C);
        SkijaUi.line(canvas, queue.x(), queue.y(), queue.x(), queue.y() + queue.height(), 0.6F, OUTLINE);
        SkijaUi.text(canvas, "UP NEXT", queue.x() + 13, queue.y() + 16, 10, secondary(), 5);
        SkijaUi.boldText(canvas, "Play queue", queue.x() + 13, queue.y() + 30, 22, TEXT, 13);
        List<Music> songs = safeCopy(CloudMusic.playList);
        if (songs.isEmpty()) {
            SkijaUi.text(canvas, "Queue is empty", queue.x() + 13, queue.y() + 60,
                    13, TEXT_MUTED, 7);
            return;
        }
        int visibleRows = Math.max(1, (int) ((queue.height() - 63) / 37));
        int start = Math.max(0, Math.min(CloudMusic.curIdx, songs.size() - visibleRows));
        float rowY = queue.y() + 58;
        for (int slot = 0; slot < visibleRows && start + slot < songs.size(); slot++) {
            int index = start + slot;
            Music music = songs.get(index);
            UiControls.Box row = new UiControls.Box(queue.x() + 8, rowY + slot * 37,
                    queue.width() - 16, 33);
            boolean playing = music.equals(current);
            if (row.contains(mouseX, mouseY) || playing) {
                SkijaUi.rounded(canvas, row.x(), row.y(), row.width(), row.height(), 2,
                        playing ? setAlpha(accent(), 31) : 0x0FFFFFFF);
            }
            UiControls.Box cover = new UiControls.Box(row.x() + 21, row.y() + 4, 25, 25);
            drawCover(canvas, "music:" + music.cacheKey(), music.getCoverUrl(64), cover, 1);
            SkijaUi.text(canvas, playing ? "||" : String.format("%02d", index + 1), row.x() + 2,
                    row.y() + 10, 12, playing ? accent() : 0x48FFFFFF, 5);
            SkijaUi.text(canvas, UiControls.ellipsize(music.getName(), row.width() - 79), row.x() + 51,
                    row.y() + 5, 12, playing ? accent() : TEXT, 6.5F);
            SkijaUi.text(canvas, UiControls.ellipsize(music.getArtistsName(), row.width() - 79), row.x() + 51,
                    row.y() + 17, 10, TEXT_MUTED, 5.5F);
            String duration = formatDuration(music.getDuration());
            SkijaUi.text(canvas, duration, row.x() + row.width() - SkijaUi.textWidth(duration, 5) - 3,
                    row.y() + 11, 10, 0x62FFFFFF, 5);
            clickRegions.add(new ClickRegion(Action.PLAY_SONG, new SongClick(songs, index), row));
        }
    }

    private static void drawLyricLine(Canvas canvas, String raw, float x, float y, float width,
                                      int color, float size) {
        SkijaUi.text(canvas, UiControls.ellipsize(raw, width), x, y, 17, color, size);
    }

    private void drawWaveform(Canvas canvas, float x, float y, float width, float height,
                              AudioPlayer player) {
        int bars = Math.max(18, Math.min(spectrumBars.length, (int) (width / 4.2F)));
        float step = width / bars;
        float[] bands = AudioPlayer.bandValues;
        boolean active = player != null && !player.isPausing() && bands != null && bands.length > 1;
        for (int i = 0; i < bars; i++) {
            float target = active ? spectrumLevel(bands, i, bars) : 0.0F;
            target = UiControls.clamp((float) Math.sqrt(Math.max(0, target)) * 1.18F, 0, 1);
            float speed = target > spectrumBars[i] ? 15.0F : 7.0F;
            spectrumBars[i] += (target - spectrumBars[i]) * Math.min(1.0F, frameDelta * speed);
            float amount = 0.08F + spectrumBars[i] * 0.92F;
            float barHeight = Math.max(2, height * amount);
            int alpha = 80 + Math.round(spectrumBars[i] * 175.0F);
            int color = (alpha << 24) | (accent() & 0x00FFFFFF);
            SkijaUi.rounded(canvas, x + i * step, y + (height - barHeight) * 0.5F,
                    Math.max(1, step * 0.48F), barHeight, 1, color);
        }
    }

    private static float spectrumLevel(float[] bands, int bar, int barCount) {
        float fromRatio = (float) Math.pow(bar / (float) barCount, 1.55);
        float toRatio = (float) Math.pow((bar + 1.0F) / barCount, 1.55);
        int from = Math.max(0, Math.min(bands.length - 1, (int) (fromRatio * bands.length)));
        int to = Math.max(from + 1, Math.min(bands.length, (int) Math.ceil(toRatio * bands.length)));
        float peak = 0;
        for (int index = from; index < to; index++) {
            float value = bands[index];
            if (Float.isFinite(value)) {
                peak = Math.max(peak, value);
            }
        }
        return peak;
    }

    private static void drawPlayDisc(Canvas canvas, float centerX, float centerY, float radius,
                                     int foreground, int background) {
        try (Paint paint = new Paint().setAntiAlias(true).setColor(background)) {
            canvas.drawCircle(centerX, centerY, radius, paint);
        }
        SkijaUi.text(canvas, ">", centerX - 2.2F, centerY - 4.4F, 9, foreground, 6);
    }

    private static void drawVolumeKnob(Canvas canvas, UiControls.Box box, float volume) {
        float x = box.x() + box.width() * UiControls.clamp(volume, 0, 1);
        float y = box.y() + box.height() * 0.5F;
        try (Paint paint = new Paint().setAntiAlias(true).setColor(0xFFF4FFFF)) {
            canvas.drawCircle(x, y, 2.4F, paint);
        }
    }

    private void drawSetsunaLogo(Canvas canvas, float x, float y, float size) {
        try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(LOGO_TEXTURE)) {
            if (borrowed != null) {
                Rect source = Rect.makeXYWH(0, 0, borrowed.image().getWidth(), borrowed.image().getHeight());
                canvas.drawImageRect(borrowed.image(), source, Rect.makeXYWH(x, y, size, size));
                return;
            }
        } catch (Throwable ignored) {
            // Keep the music screen usable while textures are still uploading.
        }
        SkijaUi.boldText(canvas, "S", x + 3, y, size, accent(), size * 0.65F);
    }

    private void drawPlayerButton(Canvas canvas, UiControls.Box box, String label, boolean enabled) {
        float hover = animateInteraction("player:" + label + ':' + Math.round(box.x()),
                enabled && box.contains(mouseX, mouseY));
        int fill = !enabled ? 0x0AFFFFFF : withAlpha(accent(), hover * 0.28F);
        SkijaUi.rounded(canvas, box.x(), box.y(), box.width(), box.height(), box.height() * 0.5F, fill);
        drawCenteredIcon(canvas, label, box, enabled ? mixColor(TEXT, accent(), hover)
                        : 0x48FFFFFF, 10,
                SkijaUi.IconSet.TRITIUM_CONTROLS);
    }

    private void drawLikeButton(Canvas canvas, UiControls.Box box, boolean liked) {
        boolean hovered = box.contains(mouseX, mouseY);
        float hover = animateInteraction("like:" + Math.round(box.x()), hovered);
        int fill = liked ? mixColor(setAlpha(accent(), 54), setAlpha(secondary(), 92), hover)
                : mixColor(0x12FFFFFF, setAlpha(accent(), 36), hover);
        SkijaUi.rounded(canvas, box.x(), box.y(), box.width(), box.height(), 5, fill);
        drawCenteredIcon(canvas, MUSIC_LIKE, box, liked ? secondary() : 0xB8FFFFFF, 9,
                SkijaUi.IconSet.TRITIUM_MUSIC);
    }

    private void drawMusicButton(Canvas canvas, UiControls.Box box, String label, boolean enabled,
                                 boolean primary) {
        float hover = animateInteraction("music:" + label + ':' + Math.round(box.x()),
                enabled && box.contains(mouseX, mouseY));
        int border = enabled && primary ? accent() : 0x48FFFFFF;
        int fill = !enabled ? 0x12FFFFFF
                : primary ? mixColor(accent(), secondary(), hover * 0.32F)
                : mixColor(0x12FFFFFF, 0x28FFFFFF, hover);
        SkijaUi.rounded(canvas, box.x(), box.y(), box.width(), box.height(), 5, border);
        SkijaUi.rounded(canvas, box.x() + 1, box.y() + 1, box.width() - 2, box.height() - 2, 4, fill);
        UiControls.centeredText(canvas, label, box, enabled ? TEXT : 0x5AFFFFFF, true);
    }

    private void drawSmallIconButton(Canvas canvas, UiControls.Box box, String glyph,
                                     SkijaUi.IconSet iconSet, boolean enabled, boolean danger, float size) {
        float hover = animateInteraction("small:" + glyph + ':' + Math.round(box.x()),
                enabled && box.contains(mouseX, mouseY));
        int fill = !enabled ? 0x0EFFFFFF : danger
                ? mixColor(0x1CE05252, 0x58E05252, hover)
                : mixColor(setAlpha(accent(), 28), setAlpha(accent(), 56), hover);
        SkijaUi.rounded(canvas, box.x(), box.y(), box.width(), box.height(), 5, fill);
        drawCenteredIcon(canvas, glyph, box,
                enabled ? (danger ? 0xFFFFA0A6 : TEXT) : 0x4AFFFFFF, size, iconSet);
    }

    private static void drawCenteredIcon(Canvas canvas, String glyph, UiControls.Box box, int color,
                                         float size, SkijaUi.IconSet iconSet) {
        float width = SkijaUi.iconWidth(glyph, size, iconSet);
        SkijaUi.icon(canvas, glyph, box.x() + (box.width() - width) * 0.5F,
                box.y(), box.height(), color, size, iconSet);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (closing || panelTransition < 0.92F) {
            return true;
        }
        Layout layout = layout();
        updateInputBounds(layout);
        if (!nowPlayingView && nowPlayingTransition <= 0.001F
                && search.click(event.x(), event.y(), doubleClick)) {
            focusSearchInput(doubleClick);
            return true;
        }
        blurSearchInput();
        for (ClickRegion region : List.copyOf(clickRegions)) {
            if (!region.box.contains(event.x(), event.y())) {
                continue;
            }
            if (isContentAction(region.action) && !layout.content.contains(event.x(), event.y())) {
                continue;
            }
            if (region.action == Action.PROGRESS && CloudMusic.player != null && !accountBusy) {
                draggingProgress = true;
                updateSeek(layout.progress, event.x());
                return true;
            }
            if (region.action == Action.VOLUME) {
                draggingVolume = true;
                updateVolume(layout.volume, event.x());
                return true;
            }
            handleClick(region);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        Layout layout = layout();
        if (draggingProgress) {
            updateSeek(layout.progress, event.x());
            return true;
        }
        if (draggingVolume) {
            updateVolume(layout.volume, event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingProgress || draggingVolume) {
            AudioPlayer player = accountBusy ? null : CloudMusic.player;
            if (draggingProgress && player != null && pendingSeek >= 0) {
                player.setPlaybackTime(pendingSeek);
            }
            draggingProgress = false;
            draggingVolume = false;
            pendingSeek = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout layout = layout();
        if (!layout.content.contains(mouseX, mouseY)) {
            return false;
        }
        contentScroll += (float) scrollY * 24;
        contentScroll = Math.min(0, contentScroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!nowPlayingView && search.isFocused()) {
            if (event.isConfirmation()) {
                startSearch();
                blurSearchInput();
                return true;
            }
            if (event.isEscape()) {
                blurSearchInput();
                return true;
            }
            if (searchInput != null && searchInput.keyPressed(event)) {
                return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_SPACE && CloudMusic.player != null) {
            togglePause();
            return true;
        }
        if (event.isEscape()) {
            if (nowPlayingView || nowPlayingTransition > 0.01F) {
                nowPlayingView = false;
            } else {
                onClose();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return !nowPlayingView && search.isFocused() && searchInput != null
                && searchInput.charTyped(event)
                || super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (closing) {
            return;
        }
        closing = true;
        nowPlayingView = false;
        search.blur();
    }

    @Override
    public void tick() {
        super.tick();
        if (closeReady) {
            closeReady = false;
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        disposed = true;
        cancelQrLogin();
        closeQrImage();
        QRCodeGenerator.clear();
        coverImages.values().stream().filter(java.util.Objects::nonNull).forEach(Image::close);
        coverImages.clear();
        loadingCovers.clear();
        if (customBackground != null) {
            customBackground.close();
            customBackground = null;
        }
        super.removed();
    }

    private void handleClick(ClickRegion region) {
        switch (region.action) {
            case BACK -> onClose();
            case NOW_PLAYING -> {
                if (CloudMusic.currentlyPlaying != null) {
                    nowPlayingView = true;
                }
            }
            case LIBRARY -> nowPlayingView = false;
            case SOURCE -> switchProvider((MusicProvider) region.payload);
            case PAGE -> openPage((Page) region.payload);
            case PLAYLIST -> openPlaylist((PlayList) region.payload);
            case PLAY_SONG -> {
                SongClick song = (SongClick) region.payload;
                play(song.source, song.index);
            }
            case PLAY_ALL -> play(playlistSongs, 0);
            case SHUFFLE -> {
                List<Music> shuffled = new ArrayList<>(playlistSongs);
                Collections.shuffle(shuffled);
                play(shuffled, 0);
            }
            case PREVIOUS -> {
                if (CloudMusic.player != null && !playQueued && !accountBusy) {
                    CloudMusic.prev();
                    setStatus("Previous track", false);
                }
            }
            case PAUSE -> togglePause();
            case NEXT -> {
                if (CloudMusic.player != null && !playQueued && !accountBusy) {
                    CloudMusic.next();
                    setStatus("Next track", false);
                }
            }
            case LIKE -> toggleLike((Music) region.payload);
            case RELOAD -> reloadAccount();
            case LOGOUT -> logoutCookie();
            case QR_LOGIN -> startQrLogin();
            case PROGRESS, VOLUME -> {
            }
        }
    }

    private void openPage(Page target) {
        int generation = ++pageRequestGeneration;
        page = target;
        selectedPlaylist = null;
        contentScroll = 0;
        pageTransition = 0;
        loadingPage = false;
        if (target == Page.LIKED || target == Page.DAILY) {
            visibleSongs = List.of();
        }
        if (target == Page.SEARCH) {
            focusSearchInput(false);
        } else if (target == Page.LIKED) {
            loadLiked(generation);
        } else if (target == Page.DAILY) {
            loadDaily(generation);
        }
    }

    private void openPlaylist(PlayList playlist) {
        selectedPlaylist = playlist;
        page = Page.PLAYLIST;
        playlistSongs = List.of();
        contentScroll = 0;
        pageTransition = 0;
        loadingPage = true;
        setStatus("Loading " + playlist.getName() + "...", false);
        playlist.loadMusicsWithCallback(songs -> minecraft.execute(() -> {
            if (disposed || selectedPlaylist != playlist) {
                return;
            }
            playlistSongs = safeCopy(songs);
            loadingPage = false;
            setStatus(playlistSongs.size() + " songs", false);
        }));
    }

    private void startSearch() {
        String query = search.text().trim();
        if (query.isEmpty() || searching || accountBusy) {
            if (query.isEmpty()) {
                setStatus("Search text is empty", true);
            }
            return;
        }
        searching = true;
        loadingPage = true;
        int generation = ++searchGeneration;
        page = Page.SEARCH;
        pageRequestGeneration++;
        selectedPlaylist = null;
        contentScroll = 0;
        pageTransition = 0;
        setStatus("Searching " + query + "...", false);
        MusicProvider requestedProvider = provider;
        CompletableFuture.supplyAsync(() -> requestedProvider == MusicProvider.QQ
                ? QqMusic.search(query) : CloudMusic.search(query)).whenComplete((songs, error) ->
                minecraft.execute(() -> {
                    if (disposed || provider != requestedProvider || generation != searchGeneration) {
                        return;
                    }
                    searching = false;
                    loadingPage = false;
                    if (error != null || songs == null) {
                        visibleSongs = List.of();
                        setStatus("Search failed: " + errorMessage(error), true);
                        return;
                    }
                    visibleSongs = safeCopy(songs);
                    setStatus(visibleSongs.size() + " results", false);
                }));
    }

    private void loadLiked(int generation) {
        if (provider == MusicProvider.QQ) {
            visibleSongs = List.of();
            loadingPage = false;
            setStatus("QQ Music liked songs are not available", false);
            return;
        }
        if (CloudMusic.profile == null || CloudMusic.likeList == null || CloudMusic.likeList.isEmpty()) {
            visibleSongs = List.of();
            loadingPage = false;
            setStatus("No liked songs", false);
            return;
        }
        visibleSongs = List.of();
        loadingPage = true;
        setStatus("Loading liked songs...", false);
        List<Long> likedIds = List.copyOf(CloudMusic.likeList);
        CompletableFuture.supplyAsync(() -> loadSongDetails(likedIds))
                .whenComplete((songs, error) -> minecraft.execute(() -> {
                    if (disposed || page != Page.LIKED || generation != pageRequestGeneration) {
                        return;
                    }
                    loadingPage = false;
                    if (error != null || songs == null) {
                        visibleSongs = List.of();
                        setStatus("Liked songs failed: " + errorMessage(error), true);
                        return;
                    }
                    visibleSongs = songs;
                    setStatus(visibleSongs.size() + " songs", false);
                }));
    }

    private void loadDaily(int generation) {
        visibleSongs = List.of();
        loadingPage = true;
        setStatus(provider == MusicProvider.QQ ? "Loading QQ Music toplist..." : "Loading daily songs...", false);
        if (provider == MusicProvider.QQ) {
            CompletableFuture.supplyAsync(QqMusic::toplist).whenComplete((songs, error) ->
                    minecraft.execute(() -> {
                        if (disposed || provider != MusicProvider.QQ || page != Page.DAILY
                                || generation != pageRequestGeneration) return;
                        loadingPage = false;
                        visibleSongs = error == null && songs != null ? safeCopy(songs) : List.of();
                        setStatus(error == null ? visibleSongs.size() + " songs"
                                : "QQ Music toplist failed: " + errorMessage(error), error != null);
                    }));
            return;
        }
        CompletableFuture.supplyAsync(() -> CloudMusicApi.recommendSongs().toJsonObject())
                .whenComplete((json, error) -> minecraft.execute(() -> {
                    if (disposed || page != Page.DAILY || generation != pageRequestGeneration) {
                        return;
                    }
                    loadingPage = false;
                    if (error != null || json == null) {
                        visibleSongs = List.of();
                        setStatus("Daily songs failed: " + errorMessage(error), true);
                        return;
                    }
                    JsonObject data = json.getAsJsonObject("data");
                    visibleSongs = parseSongs(data == null ? null : data.getAsJsonArray("dailySongs"));
                    setStatus(visibleSongs.size() + " songs", false);
                }));
    }

    private void loadHomeRecommendations() {
        if (loadingHome || !hasAccount()) {
            return;
        }
        loadingHome = true;
        setStatus("Loading recommendations...", false);
        if (provider == MusicProvider.QQ) {
            CompletableFuture.supplyAsync(() -> new QqHomeData(QqMusic.recommendations(), QqMusic.toplist()))
                    .whenComplete((data, error) -> minecraft.execute(() -> {
                        if (disposed || provider != MusicProvider.QQ) return;
                        loadingHome = false;
                        if (error != null || data == null) {
                            recommendations = safePlaylists(QqMusic.playLists);
                            homeSongs = List.of();
                            setStatus("QQ Music recommendations failed: " + errorMessage(error), true);
                            return;
                        }
                        recommendations = safePlaylists(data.playlists());
                        homeSongs = safeCopy(data.songs());
                        setStatus(recommendations.size() + " recommendations", false);
                    }));
            return;
        }
        CompletableFuture.supplyAsync(() -> CloudMusicApi.recommendResource().toJsonObject())
                .whenComplete((json, error) -> minecraft.execute(() -> {
                    if (disposed || provider != MusicProvider.NETEASE) {
                        return;
                    }
                    loadingHome = false;
                    if (error != null || json == null) {
                        recommendations = safePlaylists(CloudMusic.playLists);
                        setStatus("Using saved playlists", false);
                        primeHomeSongs();
                        return;
                    }
                    List<PlayList> parsed = new ArrayList<>();
                    JsonArray array = json.getAsJsonArray("recommend");
                    if (array != null) {
                        for (JsonElement element : array) {
                            JsonObject object = element.getAsJsonObject().deepCopy();
                            if (object.has("picUrl")) {
                                object.addProperty("coverImgUrl", object.get("picUrl").getAsString());
                            }
                            if (object.has("playcount")) {
                                object.addProperty("playCount", object.get("playcount").getAsLong());
                            }
                            parsed.add(JsonUtils.parse(object, PlayList.class));
                        }
                    }
                    recommendations = List.copyOf(parsed);
                    setStatus(recommendations.size() + " recommendations", false);
                    primeHomeSongs();
                }));
    }

    private void primeHomeSongs() {
        if (!homeSongs.isEmpty()) {
            return;
        }
        if (provider == MusicProvider.QQ) {
            loadHomeRecommendations();
            return;
        }
        if (CloudMusic.playList != null && !CloudMusic.playList.isEmpty()) {
            homeSongs = safeCopy(CloudMusic.playList);
            return;
        }
        List<PlayList> source = homePlaylists();
        if (source.isEmpty()) {
            return;
        }
        PlayList playlist = source.getFirst();
        playlist.loadMusicsWithCallback(songs -> minecraft.execute(() -> {
            if (!disposed && homeSongs.isEmpty()) {
                homeSongs = safeCopy(songs);
            }
        }));
    }

    private void reloadAccount() {
        if (playQueued) {
            return;
        }
        if (provider == MusicProvider.QQ) {
            if (!QqMusic.hasCredentials()) {
                startQrLogin();
                return;
            }
            if (accountBusy) return;
            accountBusy = true;
            setStatus("Restoring QQ Music account...", false);
            CompletableFuture.runAsync(QqMusic::reloadAccount).whenComplete((ignored, error) ->
                    minecraft.execute(() -> {
                        if (disposed || provider != MusicProvider.QQ) return;
                        accountBusy = false;
                        if (error != null || !QqMusic.isLoggedIn()) {
                            setStatus(error == null ? QqMusic.status
                                    : "Account restore failed: " + errorMessage(error), true);
                            startQrLogin();
                            return;
                        }
                        resetProviderContent();
                        setStatus("Logged in as " + profileName(), false);
                        loadHomeRecommendations();
                    }));
            return;
        }
        String value = CloudMusic.loadCookie();
        if (value.isBlank()) {
            startQrLogin();
            return;
        }
        if (accountBusy) {
            return;
        }
        accountBusy = true;
        setStatus("Restoring NetEase account...", false);
        CompletableFuture.runAsync(() -> CloudMusic.loadNCM(value)).whenComplete((ignored, error) ->
                minecraft.execute(() -> {
                    if (disposed || provider != MusicProvider.NETEASE) {
                        return;
                    }
                    accountBusy = false;
                    if (error != null || CloudMusic.profile == null) {
                        setStatus(error == null ? CloudMusic.status
                                : "Account restore failed: " + errorMessage(error), true);
                        startQrLogin();
                        return;
                    }
                    recommendations = List.of();
                    homeSongs = List.of();
                    setStatus("Logged in as " + CloudMusic.profile.getName(), false);
                    loadHomeRecommendations();
                }));
    }

    private void startQrLogin() {
        if (playQueued) {
            return;
        }
        cancelQrLogin();
        closeQrImage();
        QRCodeGenerator.clear();

        accountBusy = true;
        qrLoginState = CloudMusic.QrLoginState.CREATING;
        setStatus("Generating QR code...", false);

        MusicProvider requestedProvider = provider;

        Thread worker = new Thread(() -> {
            Throwable error = null;
            if (requestedProvider == MusicProvider.QQ) {
                try {
                    QrCode code = QqMusic.createQrCode();
                    qrLoginState = CloudMusic.QrLoginState.WAITING_SCAN;
                    long deadline = System.currentTimeMillis() + 180_000L;
                    while (!Thread.currentThread().isInterrupted() && System.currentTimeMillis() < deadline) {
                        QrLoginState state = QqMusic.checkQrCode(code);
                        qrLoginState = mapQqLoginState(state);
                        if (state == QrLoginState.CONFIRMED) {
                            QqMusic.reloadAccount();
                            break;
                        }
                        if (state == QrLoginState.EXPIRED || state == QrLoginState.ERROR) break;
                        Thread.sleep(2500L);
                    }
                    if (!Thread.currentThread().isInterrupted() && !QqMusic.isLoggedIn()
                            && qrLoginState != CloudMusic.QrLoginState.FAILED) {
                        qrLoginState = CloudMusic.QrLoginState.EXPIRED;
                    }
                } catch (Throwable failure) {
                    if (!(failure instanceof InterruptedException)) error = failure;
                    Thread.currentThread().interrupt();
                }
            } else {
                String cookie = CloudMusic.qrCodeLogin(state -> {
                    if (qrLoginThread == Thread.currentThread()) qrLoginState = state;
                });
                if (!cookie.isBlank() && !Thread.currentThread().isInterrupted()) {
                    try {
                        CloudMusic.loadNCM(cookie);
                    } catch (Throwable failure) {
                        error = failure;
                    }
                }
            }
            Throwable resultError = error;
            Thread completedWorker = Thread.currentThread();
            minecraft.execute(() -> finishQrLogin(completedWorker, requestedProvider, resultError));
        }, "Setsuna-" + requestedProvider.name() + "-QR-Login");
        worker.setDaemon(true);
        qrLoginThread = worker;
        worker.start();
    }

    private void finishQrLogin(Thread worker, MusicProvider requestedProvider, Throwable error) {
        if (disposed || qrLoginThread != worker || provider != requestedProvider) {
            return;
        }
        qrLoginThread = null;
        accountBusy = false;
        if (error != null || !hasAccount()) {
            statusError = qrLoginState == CloudMusic.QrLoginState.FAILED;
            setStatus(error == null ? qrLoginStatus() : "QR login failed: " + errorMessage(error), statusError);
            return;
        }

        closeQrImage();
        QRCodeGenerator.clear();
        recommendations = List.of();
        homeSongs = List.of();
        setStatus("Logged in as " + profileName(), false);
        loadHomeRecommendations();
    }

    private void logoutCookie() {
        if (accountBusy || playQueued || !hasAccount()) {
            return;
        }
        accountBusy = true;
        setStatus("Logging out...", false);
        CompletableFuture.runAsync(() -> {
            if (provider == MusicProvider.QQ) {
                QqMusic.clearLogin();
            } else {
                CloudMusic.onStop();
                OptionsUtil.setCookie("");
                CloudMusic.saveCookie("");
                CloudMusic.profile = null;
                CloudMusic.playLists = new CopyOnWriteArrayList<>();
                CloudMusic.likeList = new CopyOnWriteArrayList<>();
                CloudMusic.playList = new ArrayList<>();
                CloudMusic.currentlyPlaying = null;
                CloudMusic.status = "Not logged in";
            }
        }).whenComplete((ignored, error) -> minecraft.execute(() -> {
            if (disposed) {
                return;
            }
            accountBusy = false;
            if (error != null) {
                setStatus("Logout failed: " + errorMessage(error), true);
                return;
            }
            recommendations = List.of();
            homeSongs = List.of();
            visibleSongs = List.of();
            playlistSongs = List.of();
            selectedPlaylist = null;
            page = Page.HOME;
            setStatus("Logged out", false);
            startQrLogin();
        }));
    }

    private void initializeProvider() {
        if (hasAccount()) {
            if (recommendations.isEmpty()) loadHomeRecommendations();
            primeHomeSongs();
            return;
        }
        if (provider == MusicProvider.QQ ? !QqMusic.hasCredentials() : CloudMusic.loadCookie().isBlank()) {
            startQrLogin();
        } else {
            reloadAccount();
        }
    }

    private void switchProvider(MusicProvider target) {
        if (target == null || target == provider || playQueued
                || accountBusy && qrLoginThread == null) return;
        cancelQrLogin();
        closeQrImage();
        QRCodeGenerator.clear();
        provider = target;
        searchGeneration++;
        pageRequestGeneration++;
        resetProviderContent();
        page = Page.HOME;
        contentScroll = 0;
        pageTransition = 0;
        setStatus("Switching to " + provider.displayName() + "...", false);
        initializeProvider();
    }

    private void resetProviderContent() {
        recommendations = List.of();
        homeSongs = List.of();
        visibleSongs = List.of();
        playlistSongs = List.of();
        selectedPlaylist = null;
        loadingPage = false;
        loadingHome = false;
        searching = false;
    }

    private boolean hasAccount() {
        return provider == MusicProvider.QQ ? QqMusic.isLoggedIn() : CloudMusic.profile != null;
    }

    private String profileName() {
        if (provider == MusicProvider.QQ) {
            return QqMusic.profile == null ? "OFFLINE" : QqMusic.profile.getNickname();
        }
        return CloudMusic.profile == null ? "OFFLINE" : CloudMusic.profile.getName();
    }

    private static CloudMusic.QrLoginState mapQqLoginState(QrLoginState state) {
        return switch (state) {
            case WAITING -> CloudMusic.QrLoginState.WAITING_SCAN;
            case SCANNED -> CloudMusic.QrLoginState.WAITING_CONFIRMATION;
            case CONFIRMED -> CloudMusic.QrLoginState.AUTHORIZED;
            case EXPIRED -> CloudMusic.QrLoginState.EXPIRED;
            case ERROR -> CloudMusic.QrLoginState.FAILED;
        };
    }

    private void play(List<Music> songs, int index) {
        if (songs == null || songs.isEmpty() || playQueued || accountBusy) {
            return;
        }
        List<Music> queue = safeCopy(songs);
        int safeIndex = Math.max(0, Math.min(index, queue.size() - 1));
        Music selected = queue.get(safeIndex);
        playQueued = true;
        setStatus("Queueing " + selected.getName() + "...", false);
        CompletableFuture.runAsync(() -> CloudMusic.play(queue, safeIndex)).whenComplete((ignored, error) ->
                minecraft.execute(() -> {
                    if (disposed) {
                        return;
                    }
                    playQueued = false;
                    setStatus(error == null ? "Playing " + selected.getName()
                            : "Playback failed: " + errorMessage(error), error != null);
                }));
    }

    private void togglePause() {
        AudioPlayer player = CloudMusic.player;
        if (player == null || playQueued || accountBusy) {
            return;
        }
        try {
            if (player.isPausing()) {
                player.unpause();
                setStatus("Playback resumed", false);
            } else {
                player.pause();
                setStatus("Playback paused", false);
            }
        } catch (RuntimeException error) {
            setStatus("Playback control failed: " + errorMessage(error), true);
        }
    }

    private void toggleLike(Music music) {
        if (provider == MusicProvider.QQ || music != null && music.getProvider() == MusicProvider.QQ) {
            setStatus("QQ Music liked-song updates are not available", false);
            return;
        }
        if (music == null || CloudMusic.likeList == null || CloudMusic.profile == null) {
            return;
        }
        boolean liked = isLiked(music);
        if (liked) {
            CloudMusic.likeList.remove(music.getId());
        } else if (!CloudMusic.likeList.contains(music.getId())) {
            CloudMusic.likeList.add(music.getId());
        }
        CompletableFuture.runAsync(() -> music.setLike(!liked)).whenComplete((ignored, error) -> {
            if (error == null) {
                return;
            }
            if (liked && !CloudMusic.likeList.contains(music.getId())) {
                CloudMusic.likeList.add(music.getId());
            } else if (!liked) {
                CloudMusic.likeList.remove(music.getId());
            }
            minecraft.execute(() -> setStatus("Like update failed: " + errorMessage(error), true));
        });
    }

    private void updateSeek(UiControls.Box box, double mouseX) {
        AudioPlayer player = accountBusy ? null : CloudMusic.player;
        if (player == null || player.getTotalTimeMillis() <= 0) {
            return;
        }
        float percent = UiControls.clamp((float) ((mouseX - box.x()) / Math.max(1, box.width())), 0, 1);
        pendingSeek = player.getTotalTimeMillis() * percent;
    }

    private void updateVolume(UiControls.Box box, double mouseX) {
        float volume = UiControls.clamp((float) ((mouseX - box.x()) / Math.max(1, box.width())), 0, 1);
        preferredVolume = volume;
        preferredVolumeChanged = true;
        AudioPlayer player = accountBusy ? null : CloudMusic.player;
        if (player != null) {
            player.setVolume(volume);
            volumePlayer = player;
        }
    }

    private void syncQrImage() {
        String address = QRCodeGenerator.getLastAddress();
        if (address.isBlank() || address.equals(qrImageAddress)) {
            return;
        }
        byte[] png = QRCodeGenerator.getQrPng();
        if (png.length == 0) {
            return;
        }

        try {
            Image decoded = Image.makeFromEncoded(png);
            if (decoded == null) {
                return;
            }
            closeQrImage();
            qrImage = decoded;
            qrImageAddress = address;
        } catch (RuntimeException error) {
            setStatus("Unable to render QR code", true);
        }
    }

    private void closeQrImage() {
        if (qrImage != null) {
            qrImage.close();
            qrImage = null;
        }
        qrImageAddress = "";
    }

    private void cancelQrLogin() {
        Thread worker = qrLoginThread;
        qrLoginThread = null;
        if (worker != null) {
            worker.interrupt();
        }
        accountBusy = false;
    }

    private String qrLoginStatus() {
        return switch (qrLoginState) {
            case IDLE -> displayStatus();
            case CREATING -> "Generating QR code...";
            case WAITING_SCAN -> "Waiting for scan in " + provider.displayName();
            case WAITING_CONFIRMATION -> "Scanned - confirm login on your phone";
            case AUTHORIZED -> "Login confirmed, loading your library...";
            case EXPIRED -> "QR code expired - generate a new one";
            case FAILED -> "QR login request failed - try again";
        };
    }

    private void drawCover(Canvas canvas, String key, String url, UiControls.Box box, float radius) {
        Image image = coverImages.get(key);
        if (image == null) {
            SkijaUi.rounded(canvas, box.x(), box.y(), box.width(), box.height(), radius, 0xFF4B303E);
            requestCover(key, url);
            return;
        }
        int save = canvas.save();
        try {
            canvas.clipRRect(RRect.makeXYWH(box.x(), box.y(), box.width(), box.height(), radius));
            float imageWidth = image.getWidth();
            float imageHeight = image.getHeight();
            float viewportAspect = box.width() / box.height();
            float imageAspect = imageWidth / imageHeight;
            float sourceWidth = imageWidth;
            float sourceHeight = imageHeight;
            if (viewportAspect > imageAspect) {
                sourceHeight = imageWidth / viewportAspect;
            } else {
                sourceWidth = imageHeight * viewportAspect;
            }
            float sourceX = (imageWidth - sourceWidth) * 0.5F;
            float sourceY = (imageHeight - sourceHeight) * 0.5F;
            canvas.drawImageRect(image,
                    Rect.makeXYWH(sourceX, sourceY, sourceWidth, sourceHeight),
                    Rect.makeXYWH(box.x(), box.y(), box.width(), box.height()),
                    SamplingMode.MITCHELL, COVER_PAINT, true);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private void requestCover(String key, String url) {
        if (url == null || url.isBlank() || loadingCovers.contains(key)
                || coverRetryAt.getOrDefault(key, 0L) > System.currentTimeMillis()) {
            return;
        }
        loadingCovers.add(key);
        CompletableFuture.supplyAsync(() -> downloadCover(url)).whenComplete((bytes, error) ->
                minecraft.execute(() -> {
                    loadingCovers.remove(key);
                    if (disposed) {
                        return;
                    }
                    if (error != null || bytes == null || bytes.length == 0) {
                        coverRetryAt.put(key, System.currentTimeMillis() + 30_000L);
                        return;
                    }
                    try {
                        Image image = Image.makeFromEncoded(bytes);
                        if (image == null) {
                            coverRetryAt.put(key, System.currentTimeMillis() + 30_000L);
                            return;
                        }
                        Image old = coverImages.put(key, image);
                        if (old != null) {
                            old.close();
                        }
                        trimCoverCache();
                    } catch (RuntimeException decodeError) {
                        coverRetryAt.put(key, System.currentTimeMillis() + 30_000L);
                    }
                }));
    }

    private static byte[] downloadCover(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "Setsuna/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = COVER_HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return response.statusCode() >= 200 && response.statusCode() < 300 ? response.body() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void trimCoverCache() {
        while (coverImages.size() > 96) {
            String key = coverImages.keySet().iterator().next();
            Image image = coverImages.remove(key);
            if (image != null) {
                image.close();
            }
        }
    }

    private float playbackProgress(AudioPlayer player) {
        if (player == null || player.getTotalTimeMillis() <= 0) {
            return 0;
        }
        float time = draggingProgress && pendingSeek >= 0 ? pendingSeek : player.getCurrentTimeMillis();
        return UiControls.clamp(time / player.getTotalTimeMillis(), 0, 1);
    }

    private float currentVolume(AudioPlayer player) {
        if (player == null) {
            return preferredVolume;
        }
        if (player != volumePlayer) {
            volumePlayer = player;
            if (preferredVolumeChanged) {
                player.setVolume(preferredVolume);
            } else {
                preferredVolume = UiControls.clamp(player.getVolume(), 0, 1);
            }
        }
        return preferredVolumeChanged ? preferredVolume : UiControls.clamp(player.getVolume(), 0, 1);
    }

    private float currentTime(AudioPlayer player) {
        if (draggingProgress && pendingSeek >= 0) {
            return pendingSeek;
        }
        return player == null ? 0 : player.getCurrentTimeMillis();
    }

    private float totalTime(Music music, AudioPlayer player) {
        return player == null || player.getTotalTimeMillis() <= 0
                ? music.getDuration() : player.getTotalTimeMillis();
    }

    private static boolean isContentAction(Action action) {
        return action == Action.PLAYLIST || action == Action.PLAY_SONG
                || action == Action.PLAY_ALL || action == Action.SHUFFLE;
    }

    private boolean isLiked(Music music) {
        return music != null && music.getProvider() == MusicProvider.NETEASE
                && CloudMusic.likeList != null && CloudMusic.likeList.contains(music.getId());
    }

    private void clampScroll(float viewportHeight, float contentHeight) {
        float max = Math.max(0, contentHeight - viewportHeight);
        contentScroll = UiControls.clamp(contentScroll, -max, 0);
    }

    private static boolean intersects(UiControls.Box first, UiControls.Box second) {
        return first.x() < second.x() + second.width()
                && first.x() + first.width() > second.x()
                && first.y() < second.y() + second.height()
                && first.y() + first.height() > second.y();
    }

    private List<PlayList> homePlaylists() {
        List<PlayList> saved = provider == MusicProvider.QQ ? QqMusic.playLists : CloudMusic.playLists;
        return recommendations.isEmpty() ? safePlaylists(saved) : recommendations;
    }

    private String displayStatus() {
        if (searching || accountBusy || playQueued || loadingPage || loadingHome || statusError) {
            return status;
        }
        Music playing = CloudMusic.currentlyPlaying;
        if (playing != null && playing.getProvider() == provider
                && CloudMusic.status != null && !CloudMusic.status.isBlank()) {
            return CloudMusic.status;
        }
        String providerStatus = provider == MusicProvider.QQ ? QqMusic.status : CloudMusic.status;
        return providerStatus == null || providerStatus.isBlank() ? status : providerStatus;
    }

    private void setStatus(String status, boolean error) {
        this.status = status == null ? "" : status;
        this.statusError = error;
    }

    private void updateInputBounds(Layout layout) {
        search.setBounds(layout.search);
        if (searchInput != null) {
            searchInput.setRectangle(
                    Math.round(layout.search.x()), Math.round(layout.search.y()),
                    Math.max(1, Math.round(layout.search.width())),
                    Math.max(1, Math.round(layout.search.height())));
        }
    }

    private void focusSearchInput(boolean selectAll) {
        search.focus();
        if (searchInput == null) return;
        searchInput.active = true;
        setFocused(searchInput);
        searchInput.setCursorPosition(searchInput.getValue().length());
        searchInput.setHighlightPos(selectAll ? 0 : searchInput.getCursorPosition());
    }

    private void blurSearchInput() {
        search.blur();
        if (searchInput == null) return;
        if (getFocused() == searchInput) setFocused(null);
        else searchInput.setFocused(false);
        searchInput.active = false;
    }

    private Layout layout() {
        return nowPlayingView || nowPlayingTransition > 0.5F ? nowPlayingLayout() : libraryLayout();
    }

    private Layout libraryLayout() {
        UiControls.Box frame = windowFrame();
        float frameWidth = frame.width();
        float frameHeight = frame.height();
        float x = frame.x();
        float y = frame.y();
        boolean compact = frameHeight < 250 || frameWidth < 430;
        float sideWidth = UiControls.clamp(frameWidth * 0.11F, compact ? 56 : 62, 76);
        UiControls.Box sidebar = new UiControls.Box(x, y, sideWidth, frameHeight);
        UiControls.Box main = new UiControls.Box(x + sideWidth, y,
                frameWidth - sideWidth, frameHeight);
        UiControls.Box player = new UiControls.Box(main.x(), y + frameHeight - PLAYER_HEIGHT,
                main.width(), PLAYER_HEIGHT);
        UiControls.Box content = new UiControls.Box(main.x(), y + TITLE_HEIGHT, main.width(),
                Math.max(1, player.y() - (y + TITLE_HEIGHT)));
        UiControls.Box back = new UiControls.Box(x + frameWidth - 27, y + 8, 18, 18);

        float avatarSize = compact ? 20 : 25;
        UiControls.Box avatar = new UiControls.Box(sidebar.x() + (sidebar.width() - avatarSize) * 0.5F,
                y + frameHeight - PLAYER_HEIGHT - avatarSize - 12, avatarSize, avatarSize);
        float navY = y + TITLE_HEIGHT + 14;
        float searchWidth = Math.min(210, Math.max(72, Math.min(main.width() * 0.48F, main.width() - 155)));
        UiControls.Box searchBox = new UiControls.Box(main.x() + 13, y + 9, searchWidth, 18);
        UiControls.Box reload = new UiControls.Box(sidebar.x() + 8,
                y + frameHeight - 27, 20, 18);
        UiControls.Box logout = new UiControls.Box(sidebar.x() + sidebar.width() - 28,
                y + frameHeight - 27, 20, 18);

        float loginWidth = Math.min(286, Math.max(120, content.width() - 30));
        float loginX = content.x() + (content.width() - loginWidth) * 0.5F;
        float qrSize = UiControls.clamp(content.height() - 80, 64, 112);
        float loginHeight = qrSize + 65;
        float loginY = content.y() + Math.max(5, (content.height() - loginHeight) * 0.5F);
        UiControls.Box qrCode = new UiControls.Box(
                content.x() + (content.width() - qrSize) * 0.5F, loginY + 22, qrSize, qrSize);
        UiControls.Box qrLogin = new UiControls.Box(loginX, qrCode.y() + qrSize + 6, loginWidth, 21);

        UiControls.Box progress = new UiControls.Box(player.x() + 8, player.y() + 2,
                player.width() - 16, 2);
        UiControls.Box playerCover = new UiControls.Box(player.x() + 8, player.y() + 9, 31, 31);
        float center = player.x() + player.width() * 0.54F;
        UiControls.Box previous = new UiControls.Box(center - 38, player.y() + 12, 20, 20);
        UiControls.Box pause = new UiControls.Box(center - 8, player.y() + 12, 20, 20);
        UiControls.Box next = new UiControls.Box(center + 22, player.y() + 12, 20, 20);
        boolean compactPlayer = player.width() < 300;
        float rightX = player.x() + player.width() - (compactPlayer ? 53 : 69);
        UiControls.Box like = compactPlayer
                ? new UiControls.Box(player.x() + player.width() - 39, player.y() + 8, 17, 17)
                : new UiControls.Box(rightX - 38, player.y() + 9, 18, 18);
        UiControls.Box time = compactPlayer
                ? new UiControls.Box(player.x() + player.width() - 1, player.y() + 7, 0, 12)
                : new UiControls.Box(rightX, player.y() + 7, 61, 12);
        UiControls.Box volume = new UiControls.Box(rightX, player.y() + 28,
                compactPlayer ? 45 : 56, 3);
        float trackTextWidth = Math.max(24, previous.x() - (playerCover.x() + playerCover.width() + 12));
        return new Layout(frame, sidebar, main, content, player, back, avatar, navY, searchBox, reload, logout,
                qrCode, qrLogin, progress, playerCover, previous, pause, next, like, time, volume,
                trackTextWidth, compact);
    }

    private Layout nowPlayingLayout() {
        UiControls.Box frame = windowFrame();
        float frameWidth = frame.width();
        float frameHeight = frame.height();
        boolean compact = frameWidth < 520 || frameHeight < 260;
        float queueWidth = compact ? 0 : UiControls.clamp(frameWidth * 0.245F, 112, 190);
        float leftWidth = UiControls.clamp(frameWidth * (compact ? 0.32F : 0.29F), 92, 215);
        float centerX = frame.x() + 18 + Math.min(leftWidth - 18, Math.min(leftWidth - 18,
                Math.max(72, frameHeight - 110))) + (compact ? 12 : 22);
        float centerRight = queueWidth > 0 ? frame.x() + frameWidth - queueWidth - 18
                : frame.x() + frameWidth - 12;
        float centerWidth = Math.max(92, centerRight - centerX);
        float controlsY = frame.y() + frameHeight - 39;
        float controlsCenter = centerX + centerWidth * 0.5F;
        UiControls.Box previous = new UiControls.Box(controlsCenter - 46, controlsY, 22, 22);
        UiControls.Box pause = new UiControls.Box(controlsCenter - 12, controlsY - 2, 26, 26);
        UiControls.Box next = new UiControls.Box(controlsCenter + 26, controlsY, 22, 22);
        UiControls.Box progress = new UiControls.Box(centerX, controlsY - 10, centerWidth, 2);
        UiControls.Box volume = new UiControls.Box(centerRight - Math.min(62, centerWidth * 0.34F),
                frame.y() + frameHeight - 10, Math.min(62, centerWidth * 0.34F), 2);
        UiControls.Box close = new UiControls.Box(frame.x() + frameWidth - 30, frame.y() + 7, 20, 20);
        UiControls.Box all = frame;
        UiControls.Box empty = new UiControls.Box(0, 0, 0, 0);
        return new Layout(frame, empty, all, all, empty, close, empty, 0, empty, empty, empty,
                empty, empty, progress, empty, previous, pause, next, empty, empty, volume,
                centerWidth, compact);
    }

    private UiControls.Box windowFrame() {
        float availableWidth = Math.max(1, width - 16.0F);
        float availableHeight = Math.max(1, height - 16.0F);
        float frameWidth = Math.min(640.0F, Math.max(320.0F, width * 0.72F));
        float frameHeight = Math.min(430.0F, Math.max(220.0F, height * 0.82F));
        frameWidth = Math.min(frameWidth, availableWidth);
        frameHeight = Math.min(frameHeight, availableHeight);
        return new UiControls.Box((width - frameWidth) * 0.5F, (height - frameHeight) * 0.5F,
                frameWidth, frameHeight);
    }

    private void updateViewTransition() {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, Math.max(0, (now - lastFrameNanos) / 1_000_000_000.0F));
        lastFrameNanos = now;
        frameDelta = delta;
        float panelTarget = closing ? 0.0F : 1.0F;
        panelTransition += (panelTarget - panelTransition)
                * Math.min(1.0F, delta * (closing ? 12.0F : 8.0F));
        pageTransition += (1.0F - pageTransition) * Math.min(1.0F, delta * 8.0F);
        float target = nowPlayingView ? 1.0F : 0.0F;
        float speed = nowPlayingView ? 7.5F : 10.5F;
        nowPlayingTransition += (target - nowPlayingTransition) * Math.min(1.0F, delta * speed);
        if (Math.abs(target - nowPlayingTransition) < 0.001F) {
            nowPlayingTransition = target;
        }
        if (closing && panelTransition < 0.01F) {
            closeReady = true;
        }
    }

    private static float easeOutCubic(float value) {
        float t = UiControls.clamp(value, 0, 1);
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse;
    }

    private float animateInteraction(String key, boolean active) {
        float current = interactionAnimations.getOrDefault(key, 0.0F);
        float target = active ? 1.0F : 0.0F;
        current += (target - current) * Math.min(1.0F, frameDelta * 12.0F);
        if (Math.abs(target - current) < 0.002F) {
            current = target;
        }
        interactionAnimations.put(key, current);
        return current;
    }

    private static int withAlpha(int color, float amount) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * UiControls.clamp(amount, 0, 1));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int mixColor(int from, int to, float amount) {
        float value = UiControls.clamp(amount, 0, 1);
        int alpha = Math.round(((from >>> 24) & 0xFF)
                + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * value);
        int red = Math.round(((from >>> 16) & 0xFF)
                + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * value);
        int green = Math.round(((from >>> 8) & 0xFF)
                + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * value);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * value);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int setAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private int accent() {
        return NetEaseMusicModule.INSTANCE.colorPreset.get().accent();
    }

    private int secondary() {
        return NetEaseMusicModule.INSTANCE.colorPreset.get().secondary();
    }

    private int backgroundColor() {
        return NetEaseMusicModule.INSTANCE.colorPreset.get().background();
    }

    private int surfaceColor() {
        return NetEaseMusicModule.INSTANCE.colorPreset.get().surface();
    }

    private int sidebarColor() {
        return NetEaseMusicModule.INSTANCE.colorPreset.get().sidebar();
    }

    private int cardColor() {
        return NetEaseMusicModule.INSTANCE.colorPreset.get().card();
    }

    private static void drawImageCover(Canvas canvas, Image image, UiControls.Box box, int alpha) {
        if (image == null || box.width() <= 0 || box.height() <= 0) {
            return;
        }
        float imageWidth = image.getWidth();
        float imageHeight = image.getHeight();
        float viewportAspect = box.width() / box.height();
        float imageAspect = imageWidth / imageHeight;
        float sourceWidth = imageWidth;
        float sourceHeight = imageHeight;
        if (viewportAspect > imageAspect) {
            sourceHeight = imageWidth / viewportAspect;
        } else {
            sourceWidth = imageHeight * viewportAspect;
        }
        float sourceX = (imageWidth - sourceWidth) * 0.5F;
        float sourceY = (imageHeight - sourceHeight) * 0.5F;
        int save = canvas.save();
        try {
            canvas.clipRRect(RRect.makeXYWH(box.x(), box.y(), box.width(), box.height(), 6));
            BACKGROUND_PAINT.setAlpha(Math.max(0, Math.min(255, alpha)));
            canvas.drawImageRect(image,
                    Rect.makeXYWH(sourceX, sourceY, sourceWidth, sourceHeight),
                    Rect.makeXYWH(box.x(), box.y(), box.width(), box.height()),
                    SamplingMode.MITCHELL, BACKGROUND_PAINT, true);
        } finally {
            BACKGROUND_PAINT.setAlpha(255);
            canvas.restoreToCount(save);
        }
    }

    private static List<Music> parseSongs(JsonArray array) {
        List<Music> songs = new ArrayList<>();
        if (array != null) {
            for (JsonElement element : array) {
                songs.add(JsonUtils.parse(CloudMusic.normalizeSong(element.getAsJsonObject()), Music.class));
            }
        }
        return List.copyOf(songs);
    }

    private static List<Music> loadSongDetails(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Music> songs = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += 400) {
            List<Long> batch = ids.subList(start, Math.min(ids.size(), start + 400));
            JsonObject response = CloudMusicApi.songDetail(batch).toJsonObject();
            songs.addAll(parseSongs(response.getAsJsonArray("songs")));
        }
        return List.copyOf(songs);
    }

    private static List<Music> safeCopy(List<Music> songs) {
        return songs == null || songs.isEmpty() ? List.of() : List.copyOf(songs);
    }

    private static List<PlayList> safePlaylists(List<PlayList> playlists) {
        return playlists == null || playlists.isEmpty() ? List.of() : List.copyOf(playlists);
    }

    private static String playlistInfo(PlayList playlist) {
        int count = playlist.musicsLoaded && playlist.musics != null ? playlist.musics.size() : playlist.getCount();
        return count + " songs / " + formatCount(playlist.getPlayCount()) + " plays";
    }

    private static String formatCount(long count) {
        if (count >= 100_000_000) {
            return String.format("%.1fB", count / 100_000_000.0);
        }
        if (count >= 10_000) {
            return String.format("%.1fW", count / 10_000.0);
        }
        if (count >= 1_000) {
            return String.format("%.1fK", count / 1_000.0);
        }
        return Long.toString(count);
    }

    private static String formatDuration(float millis) {
        long seconds = Math.max(0, Math.round(millis / 1000.0));
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    private static String qualityLabel(Music music) {
        if (music.isHiRes()) return "HI-RES";
        if (music.isDolbyAtmos()) return "DOLBY";
        if (music.isInstrumental()) return "INST";
        if (music.isDirty()) return "EXPLICIT";
        return "NCM";
    }

    private static String firstLetter(String value) {
        return value == null || value.isBlank() ? "?" : value.substring(0, 1).toUpperCase();
    }

    private String pageTitle() {
        return switch (page) {
            case SEARCH -> "Search Results";
            case LIKED -> "Liked Songs";
            case DAILY -> "Daily Songs";
            case PLAYLIST -> selectedPlaylist == null ? "Playlist" : selectedPlaylist.getName();
            case HOME -> "Home";
        };
    }

    private static String errorMessage(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        Throwable cause = error.getCause() == null ? error : error.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private enum Page {
        HOME,
        SEARCH,
        LIKED,
        DAILY,
        PLAYLIST
    }

    private enum Action {
        BACK,
        NOW_PLAYING,
        LIBRARY,
        PAGE,
        SOURCE,
        PLAYLIST,
        PLAY_SONG,
        PLAY_ALL,
        SHUFFLE,
        PREVIOUS,
        PAUSE,
        NEXT,
        LIKE,
        RELOAD,
        LOGOUT,
        QR_LOGIN,
        PROGRESS,
        VOLUME
    }

    private record ClickRegion(Action action, Object payload, UiControls.Box box) {
    }

    private record SongClick(List<Music> source, int index) {
    }

    private record QqHomeData(List<PlayList> playlists, List<Music> songs) {
    }

    private record Layout(
            UiControls.Box frame,
            UiControls.Box sidebar,
            UiControls.Box main,
            UiControls.Box content,
            UiControls.Box player,
            UiControls.Box back,
            UiControls.Box avatar,
            float navY,
            UiControls.Box search,
            UiControls.Box reload,
            UiControls.Box logout,
            UiControls.Box qrCode,
            UiControls.Box qrLogin,
            UiControls.Box progress,
            UiControls.Box playerCover,
            UiControls.Box previous,
            UiControls.Box pause,
            UiControls.Box next,
            UiControls.Box like,
            UiControls.Box time,
            UiControls.Box volume,
            float trackTextWidth,
            boolean compact
    ) {
    }
}
