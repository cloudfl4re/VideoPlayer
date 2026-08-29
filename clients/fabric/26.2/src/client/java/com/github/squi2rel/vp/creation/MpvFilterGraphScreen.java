package com.github.squi2rel.vp.creation;

import com.github.squi2rel.mcng.core.GraphDocument;
import com.github.squi2rel.mcng.core.GraphJsonCodec;
import com.github.squi2rel.mcng.fabric.client.GraphEditorBounds;
import com.github.squi2rel.mcng.fabric.client.GraphEditorComponent;
import com.github.squi2rel.mcng.fabric.client.GraphEditorHost;
import com.github.squi2rel.mcng.fabric.client.GraphEditorI18n;
import com.github.squi2rel.mcng.fabric.client.GraphEditorSession;
import com.github.squi2rel.mcng.fabric.client.GraphEditorTheme;
import com.github.squi2rel.mcng.fabric.client.GraphEditorUiConfig;
import com.github.squi2rel.mcng.fabric.client.NodeComponentRegistry;
import com.github.squi2rel.vp.filtergraph.MpvFilterGraphCompiler;
import com.github.squi2rel.vp.filtergraph.MpvFilterGraphManager;
import com.github.squi2rel.vp.filtergraph.MpvFilterGraphNodes;
import com.github.squi2rel.vp.filtergraph.MpvFilterGraphTypes;
import com.github.squi2rel.vp.filtergraph.MpvLavfiFilterCatalog;
import com.github.squi2rel.vp.i18n.VpTexts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class MpvFilterGraphScreen extends Screen implements GraphEditorHost {
    private static final VpUiTheme THEME = VpUiTheme.classic();
    private static final int TOP_BAR_HEIGHT = 28;
    private static final long AUTO_APPLY_DELAY_MS = 500L;
    private static final GraphEditorI18n MINECRAFT_I18N = (key, fallback, args) ->
            Language.getInstance().has(key) ? I18n.get(key, args) : GraphEditorI18n.formatFallback(fallback, key, args);

    private final Screen parent;
    private final GraphJsonCodec codec = new GraphJsonCodec();
    private final GraphEditorSession session;
    private final GraphEditorComponent editor;

    private VpButtonWidget applyButton;
    private VpButtonWidget autoApplyButton;
    private String status = "";
    private boolean statusError;
    private long autoApplyAt = -1L;

    public MpvFilterGraphScreen(Screen parent) {
        super(VpTexts.tr("screen.videoplayer.mpv_filter_graph", "MPV Filter Graph"));
        this.parent = parent;
        GraphDocument document = MpvFilterGraphManager.document();
        this.session = new GraphEditorSession(
                MpvFilterGraphNodes.createRegistry(),
                MpvFilterGraphTypes.createRegistry(),
                codec,
                document,
                this
        );
        this.editor = new GraphEditorComponent(
                session,
                MpvFilterGraphNodes.createPalette(),
                new NodeComponentRegistry(),
                GraphEditorUiConfig.defaultConfig().withTheme(GraphEditorTheme.classic())
        );
    }

    @Override
    protected void init() {
        applyButton = new VpButtonWidget(width - 176, 5, 72, 18,
                VpTexts.tr("button.videoplayer.apply_filter", "Apply"), button -> applyNow(), THEME);
        autoApplyButton = new VpButtonWidget(width - 96, 5, 88, 18, autoApplyText(), button -> toggleAutoApply(), THEME)
                .selected(MpvFilterGraphManager.autoApply());
        addRenderableWidget(applyButton);
        addRenderableWidget(autoApplyButton);
        editor.init(font, editorBounds());
        syncStatusFromCompile();
    }

    @Override
    public void tick() {
        super.tick();
        if (autoApplyAt > 0 && System.currentTimeMillis() >= autoApplyAt) {
            autoApplyAt = -1L;
            applyNow();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        editor.setBounds(editorBounds());
        editor.render(context, font, mouseX, mouseY, delta);
        context.fill(0, 0, width, TOP_BAR_HEIGHT, THEME.panelBackgroundColor());
        context.text(font, title, 8, 10, THEME.primaryTextColor());
        int statusRight = Math.max(80, width - 184);
        String visible = font.plainSubstrByWidth(status == null ? "" : status, statusRight - 90);
        context.text(font, Component.literal(visible), 90, 10, statusColor());
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) return true;
        return editor.mouseClicked(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        return editor.mouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY) || super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        return editor.mouseReleased(click.x(), click.y(), click.button()) || super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return editor.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount) || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (editor.keyPressed(input.key(), input.scancode(), input.modifiers())) return true;
        if (input.key() == GLFW.GLFW_KEY_DELETE || input.key() == GLFW.GLFW_KEY_BACKSPACE) {
            session.removeSelectedNodes();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (input.isAllowedChatCharacter()) {
            String value = input.codepointAsString();
            if (value.length() == 1 && editor.charTyped(value.charAt(0), 0)) return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void onClose() {
        editor.close();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onDocumentChanged(GraphDocument document) {
        MpvFilterGraphManager.setDocument(document);
        syncStatusFromCompile();
        if (MpvFilterGraphManager.autoApply()) {
            autoApplyAt = System.currentTimeMillis() + AUTO_APPLY_DELAY_MS;
        }
    }

    @Override
    public void copyToClipboard(String value) {
        if (minecraft != null) minecraft.keyboardHandler.setClipboard(value);
    }

    @Override
    public String readClipboard() {
        return minecraft == null ? "" : minecraft.keyboardHandler.getClipboard();
    }

    @Override
    public void showMessage(String message) {
        status = message == null ? "" : message;
        statusError = false;
    }

    @Override
    public GraphEditorI18n i18n() {
        return MINECRAFT_I18N;
    }

    private void toggleAutoApply() {
        MpvFilterGraphManager.setAutoApply(!MpvFilterGraphManager.autoApply());
        autoApplyAt = -1L;
        if (autoApplyButton != null) {
            autoApplyButton.setMessage(autoApplyText());
            autoApplyButton.selected(MpvFilterGraphManager.autoApply());
        }
        setStatus(
                MpvFilterGraphManager.autoApply() ? "message.videoplayer.mpv_auto_apply_enabled" : "message.videoplayer.mpv_auto_apply_disabled",
                MpvFilterGraphManager.autoApply() ? "Automatic filter application enabled" : "Automatic filter application disabled"
        );
    }

    private void applyNow() {
        MpvFilterGraphManager.ApplyResult result = MpvFilterGraphManager.applyToActivePlayers();
        if (result.success()) {
            setStatus("message.videoplayer.mpv_filter_applied", "%1$s (%2$s active players)", result.message(), result.playerCount());
        } else {
            setStatus("error.videoplayer.mpv_filter_apply_failed", "MPV filter graph could not be applied: %s", result.message());
        }
    }

    private void syncStatusFromCompile() {
        MpvLavfiFilterCatalog.Catalog catalog = MpvLavfiFilterCatalog.get();
        if (!catalog.usable()) {
            if (catalog.available()) {
                setStatus("error.videoplayer.mpv_filter_api_no_filters", "MPV filter API returned no lavfi filters.");
            } else {
                setStatus("error.videoplayer.mpv_filter_api_unavailable", "MPV filter API unavailable: %s", catalog.error());
            }
            return;
        }
        MpvFilterGraphCompiler.CompileResult compiled = MpvFilterGraphManager.compileCurrent();
        if (compiled.success()) {
            setStatus(
                    compiled.graph().isBlank() ? "message.videoplayer.mpv_filter_saved_empty" : "message.videoplayer.mpv_filter_saved_ready",
                    compiled.graph().isBlank() ? "Saved. No active graph." : "Saved. Graph ready."
            );
        } else {
            setStatus("error.videoplayer.mpv_filter_compile", "Filter graph compile error: %s", compiled.error());
        }
    }

    private int statusColor() {
        return statusError ? THEME.errorColor() : THEME.secondaryTextColor();
    }

    private Component autoApplyText() {
        return VpTexts.tr("label.videoplayer.mpv_auto_apply", "Auto: %s",
                MpvFilterGraphManager.autoApply()
                        ? VpTexts.tr("label.videoplayer.on", "On")
                        : VpTexts.tr("label.videoplayer.off", "Off"));
    }

    private void setStatus(String key, String fallback, Object... args) {
        status = VpTexts.tr(key, fallback, args).getString();
        statusError = key.startsWith("error.");
    }

    private GraphEditorBounds editorBounds() {
        return new GraphEditorBounds(0, TOP_BAR_HEIGHT, width, Math.max(1, height - TOP_BAR_HEIGHT));
    }
}
