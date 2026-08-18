package com.github.squi2rel.vp.creation;

import com.github.squi2rel.vp.i18n.VpTexts;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

class VpButtonWidget extends AbstractWidget {
    private final VpUiTheme theme;
    private final Consumer<VpButtonWidget> onPress;
    private boolean selected;
    private boolean danger;
    private boolean clipped;
    private int clipLeft;
    private int clipTop;
    private int clipRight;
    private int clipBottom;
    private Component temporaryMessage;
    private long temporaryMessageUntil;

    VpButtonWidget(int x, int y, int width, int height, Component message, Consumer<VpButtonWidget> onPress, VpUiTheme theme) {
        super(x, y, width, height, message);
        this.theme = theme;
        this.onPress = onPress;
    }

    VpButtonWidget selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    VpButtonWidget danger(boolean danger) {
        this.danger = danger;
        return this;
    }

    VpButtonWidget clip(int left, int top, int right, int bottom) {
        this.clipped = true;
        this.clipLeft = left;
        this.clipTop = top;
        this.clipRight = right;
        this.clipBottom = bottom;
        return this;
    }

    void showTemporaryLabel(String label, long millis) {
        temporaryMessage = Component.literal(label == null ? "" : label);
        temporaryMessageUntil = System.currentTimeMillis() + Math.max(0, millis);
    }

    void showTemporaryLabel(Component label, long millis) {
        temporaryMessage = label == null ? Component.empty() : label;
        temporaryMessageUntil = System.currentTimeMillis() + Math.max(0, millis);
    }

    void showPermissionDenied() {
        showTemporaryLabel(VpTexts.tr("error.videoplayer.permission_denied", "Permission denied"), 1500L);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY) && (!clipped || insideClip(mouseX, mouseY));
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int fill = fillColor();
        int border = borderColor();
        int textColor = textColor();
        VpUiRenderer.drawBox(context, getX(), getY(), getWidth(), getHeight(), fill, border);
        Font textRenderer = Minecraft.getInstance().font;
        drawButtonText(context, textRenderer, textColor);
    }

    @Override
    public void onClick(MouseButtonEvent click, boolean doubleClick) {
        onPress.accept(this);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (!active || !visible || !input.isSelection()) return false;
        onPress.accept(this);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }

    private void drawButtonText(GuiGraphics context, Font textRenderer, int color) {
        int left = getX() + 4;
        int right = getRight() - 4;
        int innerWidth = Math.max(1, right - left);
        String label = displayMessage().getString();
        String visibleLabel = textRenderer.width(label) > innerWidth ? textRenderer.plainSubstrByWidth(label, innerWidth) : label;
        Component visibleText = Component.literal(visibleLabel);
        int textWidth = textRenderer.width(visibleLabel);
        int textX = left + Math.max(0, (innerWidth - textWidth) / 2);
        int textY = getY() + Math.max(1, (getHeight() - textRenderer.lineHeight) / 2);
        if (theme.textShadow()) {
            context.drawString(textRenderer, visibleText, textX, textY, color);
            return;
        }
        context.drawString(textRenderer, visibleText, textX, textY, color, false);
    }

    private int fillColor() {
        int base = VpUiRenderer.darken(theme.nodeBodyColor(), 0.04f);
        if (danger && (selected || isHovered())) {
            return VpUiRenderer.blend(base, theme.errorColor(), selected ? 0.22f : 0.12f);
        }
        if (selected) {
            return VpUiRenderer.blend(base, theme.accentColor(), 0.20f);
        }
        if (!active) {
            return VpUiRenderer.blend(base, theme.canvasBackgroundColor(), 0.36f);
        }
        if (isHovered()) {
            return VpUiRenderer.blend(base, theme.accentColor(), 0.11f);
        }
        return base;
    }

    private Component displayMessage() {
        if (temporaryMessage != null && System.currentTimeMillis() < temporaryMessageUntil) {
            return temporaryMessage;
        }
        temporaryMessage = null;
        return getMessage();
    }

    private int borderColor() {
        if (danger && (selected || isHovered())) return theme.errorColor();
        if (selected) return theme.accentColor();
        if (!active) return VpUiRenderer.blend(theme.panelBorderColor(), theme.canvasBackgroundColor(), 0.45f);
        if (isHovered()) return VpUiRenderer.blend(theme.panelBorderColor(), theme.accentColor(), 0.48f);
        return theme.panelBorderColor();
    }

    private int textColor() {
        if (danger && selected) return theme.errorColor();
        if (selected) return theme.primaryTextColor();
        if (!active) return VpUiRenderer.blend(theme.secondaryTextColor(), theme.canvasBackgroundColor(), 0.45f);
        return isHovered() ? theme.primaryTextColor() : theme.secondaryTextColor();
    }

    private boolean insideClip(double mouseX, double mouseY) {
        return mouseX >= clipLeft
                && mouseY >= clipTop
                && mouseX < clipRight
                && mouseY < clipBottom;
    }
}
