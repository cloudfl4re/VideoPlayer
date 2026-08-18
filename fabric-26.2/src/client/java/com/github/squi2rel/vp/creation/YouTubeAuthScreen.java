package com.github.squi2rel.vp.creation;

import com.github.squi2rel.vp.VideoPlayerClient;
import com.github.squi2rel.vp.i18n.VpTexts;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class YouTubeAuthScreen extends Screen {
    private static final VpUiTheme THEME = VpUiTheme.classic();
    private static final int PANEL_WIDTH = 460;
    private static final int PANEL_MIN_HEIGHT = 210;
    private static final int CONTROL_HEIGHT = 20;
    private static final int HINT_LINE_HEIGHT = 10;
    private static final int HINT_TOP = 110;
    private static final int HINT_BOTTOM_SPACE = 64;

    private final Screen parent;
    private VpTextFieldWidget cookiesFile;
    private VpTextFieldWidget browserSpec;
    private VpButtonWidget save;
    private VpButtonWidget clear;
    private VpButtonWidget close;
    private Component status = Component.empty();

    public YouTubeAuthScreen(Screen parent) {
        super(VpTexts.tr("screen.videoplayer.youtube_auth", "YouTube Authentication"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Layout layout = layout();
        int fieldWidth = layout.panelWidth - 48;
        cookiesFile = new VpTextFieldWidget(font, layout.left + 24, layout.top + 42, fieldWidth, CONTROL_HEIGHT,
                VpTexts.tr("label.videoplayer.youtube_cookies_file", "Netscape cookie file"), THEME);
        cookiesFile.setMaxLength(4096);
        cookiesFile.setValue(currentCookiesFile());
        browserSpec = new VpTextFieldWidget(font, layout.left + 24, layout.top + 80, fieldWidth, CONTROL_HEIGHT,
                VpTexts.tr("label.videoplayer.youtube_browser", "Browser profile (yt-dlp)"), THEME);
        browserSpec.setMaxLength(256);
        browserSpec.setValue(currentBrowserSpec());
        save = new VpButtonWidget(layout.left + 24, layout.buttonY(), 96, CONTROL_HEIGHT,
                VpTexts.tr("button.videoplayer.save", "Save"), ignored -> saveValues(), THEME);
        clear = new VpButtonWidget(layout.left + 128, layout.buttonY(), 96, CONTROL_HEIGHT,
                VpTexts.tr("button.videoplayer.clear", "Clear"), ignored -> clearValues(), THEME);
        close = new VpButtonWidget(layout.left + layout.panelWidth - 120, layout.buttonY(), 96, CONTROL_HEIGHT,
                VpTexts.tr("button.videoplayer.close", "Close"), ignored -> onClose(), THEME);
        addRenderableWidget(cookiesFile);
        addRenderableWidget(browserSpec);
        addRenderableWidget(save);
        addRenderableWidget(clear);
        addRenderableWidget(close);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0000000);
        Layout layout = layout();
        context.fill(layout.left, layout.top, layout.left + layout.panelWidth, layout.top + layout.panelHeight, THEME.panelBackgroundColor());
        context.outline(layout.left, layout.top, layout.panelWidth, layout.panelHeight, THEME.panelBorderColor());
        context.centeredText(font, title, width / 2, layout.top + 8, THEME.primaryTextColor());
        drawTrimmedLabel(context, VpTexts.tr("label.videoplayer.youtube_cookies_file", "Netscape cookie file"), layout.left + 24, layout.top + 30, layout.contentWidth);
        drawTrimmedLabel(context, VpTexts.tr("label.videoplayer.youtube_browser", "Browser profile (yt-dlp)"), layout.left + 24, layout.top + 68, layout.contentWidth);
        int hintY = layout.top + HINT_TOP;
        hintY = drawWrappedLabel(context, layout.fileHintLines, layout.left + 24, hintY);
        drawWrappedLabel(context, layout.serverHintLines, layout.left + 24, hintY + 4);
        if (!status.getString().isBlank()) {
            drawTrimmedLabel(context, status, layout.left + 24, layout.buttonY() - 16, layout.contentWidth);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void saveValues() {
        if (VideoPlayerClient.config == null) return;
        VideoPlayerClient.config.youtubeCookiesFile = cookiesFile.getValue().trim();
        VideoPlayerClient.config.youtubeCookiesFromBrowser = browserSpec.getValue().trim();
        VideoPlayerClient.saveConfig();
        VideoPlayerClient.applyNativePlatformConfig();
        status = VpTexts.tr("message.videoplayer.youtube_auth_saved", "YouTube authentication settings saved").withStyle(ChatFormatting.GREEN);
    }

    private void clearValues() {
        cookiesFile.setValue("");
        browserSpec.setValue("");
        saveValues();
        status = VpTexts.tr("message.videoplayer.youtube_auth_cleared", "YouTube authentication settings cleared").withStyle(ChatFormatting.GREEN);
    }

    private String currentCookiesFile() {
        return VideoPlayerClient.config == null || VideoPlayerClient.config.youtubeCookiesFile == null
                ? "" : VideoPlayerClient.config.youtubeCookiesFile;
    }

    private String currentBrowserSpec() {
        return VideoPlayerClient.config == null || VideoPlayerClient.config.youtubeCookiesFromBrowser == null
                ? "" : VideoPlayerClient.config.youtubeCookiesFromBrowser;
    }

    private Layout layout() {
        int panelWidth = Math.min(PANEL_WIDTH, Math.max(260, width - 24));
        int contentWidth = panelWidth - 48;
        List<FormattedCharSequence> fileHintLines = font.split(VpTexts.tr(
                "hint.videoplayer.youtube_auth_file",
                "Export a Netscape cookies.txt file from a signed-in browser. A cookie file takes priority; otherwise use a yt-dlp browser profile. Do not enter your password."
        ), contentWidth);
        List<FormattedCharSequence> serverHintLines = font.split(VpTexts.tr(
                "hint.videoplayer.youtube_auth_server",
                "This setting applies only to this client. Configure server cookies separately for server-side streams and live playback."
        ), contentWidth);
        int desiredHeight = Math.max(PANEL_MIN_HEIGHT, HINT_BOTTOM_SPACE + HINT_TOP
                + (fileHintLines.size() + serverHintLines.size()) * HINT_LINE_HEIGHT);
        int maxHeight = Math.max(1, height - 16);
        int panelHeight = Math.min(desiredHeight, maxHeight);
        int lineCapacity = Math.max(0, (panelHeight - HINT_TOP - HINT_BOTTOM_SPACE) / HINT_LINE_HEIGHT);
        int fileLines = Math.min(fileHintLines.size(), Math.max(0, (lineCapacity + 1) / 2));
        int serverLines = Math.min(serverHintLines.size(), Math.max(0, lineCapacity - fileLines));
        int remaining = lineCapacity - fileLines - serverLines;
        if (remaining > 0) {
            int extraFile = Math.min(remaining, fileHintLines.size() - fileLines);
            fileLines += extraFile;
            remaining -= extraFile;
            serverLines += Math.min(remaining, serverHintLines.size() - serverLines);
        }
        int top = Math.max(8, (height - panelHeight) / 2);
        return new Layout(panelWidth, panelHeight, contentWidth, (width - panelWidth) / 2, top,
                fileHintLines.subList(0, fileLines), serverHintLines.subList(0, serverLines));
    }

    private int drawWrappedLabel(GuiGraphicsExtractor context, List<FormattedCharSequence> lines, int x, int y) {
        int currentY = y;
        for (FormattedCharSequence line : lines) {
            context.text(font, line, x, currentY, THEME.secondaryTextColor());
            currentY += HINT_LINE_HEIGHT;
        }
        return currentY;
    }

    private void drawTrimmedLabel(GuiGraphicsExtractor context, Component text, int x, int y, int maxWidth) {
        Component visible = Component.literal(font.substrByWidth(text, Math.max(1, maxWidth)).getString());
        drawLabel(context, visible, x, y);
    }

    private void drawLabel(GuiGraphicsExtractor context, Component text, int x, int y) {
        context.text(font, text, x, y, THEME.secondaryTextColor());
    }

    private record Layout(int panelWidth, int panelHeight, int contentWidth, int left, int top,
                          List<FormattedCharSequence> fileHintLines, List<FormattedCharSequence> serverHintLines) {
        private int buttonY() {
            return top + panelHeight - 26;
        }
    }
}
