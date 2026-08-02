package com.github.squi2rel.vp.creation;

import com.github.squi2rel.vp.VideoPlayerClient;
import com.github.squi2rel.vp.i18n.VpTexts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

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
    private Text status = Text.empty();

    public YouTubeAuthScreen(Screen parent) {
        super(VpTexts.tr("screen.videoplayer.youtube_auth", "YouTube Authentication"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Layout layout = layout();
        int fieldWidth = layout.panelWidth - 48;
        cookiesFile = new VpTextFieldWidget(textRenderer, layout.left + 24, layout.top + 42, fieldWidth, CONTROL_HEIGHT,
                VpTexts.tr("label.videoplayer.youtube_cookies_file", "Netscape cookie file"), THEME);
        cookiesFile.setMaxLength(4096);
        cookiesFile.setText(currentCookiesFile());
        browserSpec = new VpTextFieldWidget(textRenderer, layout.left + 24, layout.top + 80, fieldWidth, CONTROL_HEIGHT,
                VpTexts.tr("label.videoplayer.youtube_browser", "Browser profile (yt-dlp)"), THEME);
        browserSpec.setMaxLength(256);
        browserSpec.setText(currentBrowserSpec());
        save = new VpButtonWidget(layout.left + 24, layout.buttonY(), 96, CONTROL_HEIGHT,
                VpTexts.tr("button.videoplayer.save", "Save"), ignored -> saveValues(), THEME);
        clear = new VpButtonWidget(layout.left + 128, layout.buttonY(), 96, CONTROL_HEIGHT,
                VpTexts.tr("button.videoplayer.clear", "Clear"), ignored -> clearValues(), THEME);
        close = new VpButtonWidget(layout.left + layout.panelWidth - 120, layout.buttonY(), 96, CONTROL_HEIGHT,
                VpTexts.tr("button.videoplayer.close", "Close"), ignored -> close(), THEME);
        addDrawableChild(cookiesFile);
        addDrawableChild(browserSpec);
        addDrawableChild(save);
        addDrawableChild(clear);
        addDrawableChild(close);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0000000);
        Layout layout = layout();
        context.fill(layout.left, layout.top, layout.left + layout.panelWidth, layout.top + layout.panelHeight, THEME.panelBackgroundColor());
        context.drawStrokedRectangle(layout.left, layout.top, layout.panelWidth, layout.panelHeight, THEME.panelBorderColor());
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, layout.top + 8, THEME.primaryTextColor());
        drawTrimmedLabel(context, VpTexts.tr("label.videoplayer.youtube_cookies_file", "Netscape cookie file"), layout.left + 24, layout.top + 30, layout.contentWidth);
        drawTrimmedLabel(context, VpTexts.tr("label.videoplayer.youtube_browser", "Browser profile (yt-dlp)"), layout.left + 24, layout.top + 68, layout.contentWidth);
        int hintY = layout.top + HINT_TOP;
        hintY = drawWrappedLabel(context, layout.fileHintLines, layout.left + 24, hintY);
        drawWrappedLabel(context, layout.serverHintLines, layout.left + 24, hintY + 4);
        if (!status.getString().isBlank()) {
            drawTrimmedLabel(context, status, layout.left + 24, layout.buttonY() - 16, layout.contentWidth);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void saveValues() {
        if (VideoPlayerClient.config == null) return;
        VideoPlayerClient.config.youtubeCookiesFile = cookiesFile.getText().trim();
        VideoPlayerClient.config.youtubeCookiesFromBrowser = browserSpec.getText().trim();
        VideoPlayerClient.saveConfig();
        VideoPlayerClient.applyNativePlatformConfig();
        status = VpTexts.tr("message.videoplayer.youtube_auth_saved", "YouTube authentication settings saved").formatted(Formatting.GREEN);
    }

    private void clearValues() {
        cookiesFile.setText("");
        browserSpec.setText("");
        saveValues();
        status = VpTexts.tr("message.videoplayer.youtube_auth_cleared", "YouTube authentication settings cleared").formatted(Formatting.GREEN);
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
        List<OrderedText> fileHintLines = textRenderer.wrapLines(VpTexts.tr(
                "hint.videoplayer.youtube_auth_file",
                "Export a Netscape cookies.txt file from a signed-in browser. A cookie file takes priority; otherwise use a yt-dlp browser profile. Do not enter your password."
        ), contentWidth);
        List<OrderedText> serverHintLines = textRenderer.wrapLines(VpTexts.tr(
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

    private int drawWrappedLabel(DrawContext context, List<OrderedText> lines, int x, int y) {
        int currentY = y;
        for (OrderedText line : lines) {
            context.drawTextWithShadow(textRenderer, line, x, currentY, THEME.secondaryTextColor());
            currentY += HINT_LINE_HEIGHT;
        }
        return currentY;
    }

    private void drawTrimmedLabel(DrawContext context, Text text, int x, int y, int maxWidth) {
        Text visible = Text.literal(textRenderer.trimToWidth(text, Math.max(1, maxWidth)).getString());
        drawLabel(context, visible, x, y);
    }

    private void drawLabel(DrawContext context, Text text, int x, int y) {
        context.drawTextWithShadow(textRenderer, text, x, y, THEME.secondaryTextColor());
    }

    private record Layout(int panelWidth, int panelHeight, int contentWidth, int left, int top,
                          List<OrderedText> fileHintLines, List<OrderedText> serverHintLines) {
        private int buttonY() {
            return top + panelHeight - 26;
        }
    }
}
