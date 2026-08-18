package com.github.squi2rel.vp.creation;

import com.github.squi2rel.vp.video.VideoScreen;
import com.github.squi2rel.vp.ClientPacketHandler;
import com.github.squi2rel.vp.i18n.VpTexts;
import com.github.squi2rel.vp.network.RequestResultStatus;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class VideoCreationScreen extends Screen {
    private final VideoCreationEditor editor;
    private final VideoCreationEditor.Draft draft;

    private EditBox nameField;
    private EditBox sourceField;
    private Button targetButton;
    private Button screenModeButton;
    private Button areaButton;
    private Button sourceButton;
    private Button selectionButton;
    private Button confirmButton;

    public VideoCreationScreen(VideoCreationEditor editor) {
        super(VpTexts.tr("screen.videoplayer.creation", "VideoPlayer Creation"));
        this.editor = editor;
        this.draft = editor.draft().copy();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(320, width - 40);
        int left = (width - panelWidth) / 2;
        int top = Math.max(24, height / 2 - 112);
        int row = top + 24;

        nameField = new EditBox(font, left + 88, row, panelWidth - 88, 20, VpTexts.tr("label.videoplayer.name", "Name"));
        nameField.setMaxLength(VideoScreen.MAX_NAME_BYTES);
        nameField.setFilter(VideoScreen::validNameInput);
        nameField.setValue(draft.name);
        addRenderableWidget(nameField);

        row += 28;
        targetButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            draft.target = draft.target.next();
            if (draft.target == VideoCreationEditor.Target.SCREEN && draft.areaName.isEmpty()) {
                draft.areaName = editor.areaNames().stream().findFirst().orElse("");
            }
            draft.name = suggestedName();
            nameField.setValue(draft.name);
            syncButtons();
        }).bounds(left + 88, row, panelWidth - 88, 20).build());

        row += 28;
        areaButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            List<String> names = editor.areaNames();
            if (names.isEmpty()) return;
            int index = names.indexOf(draft.areaName);
            draft.areaName = names.get((index + 1 + names.size()) % names.size());
            draft.source = "";
            syncButtons();
        }).bounds(left + 88, row, panelWidth - 88, 20).build());

        row += 28;
        screenModeButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            draft.screenMode = draft.screenMode.next();
            syncButtons();
        }).bounds(left + 88, row, panelWidth - 88, 20).build());

        row += 28;
        sourceField = new EditBox(font, left + 88, row, panelWidth - 168, 20, VpTexts.tr("label.videoplayer.source", "Source"));
        sourceField.setMaxLength(VideoScreen.MAX_NAME_BYTES);
        sourceField.setFilter(VideoScreen::validNameInput);
        sourceField.setValue(draft.source);
        addRenderableWidget(sourceField);
        sourceButton = addRenderableWidget(Button.builder(VpTexts.tr("button.videoplayer.select", "Select"), button -> {
            List<String> names = editor.realScreenNames(draft.areaName);
            if (names.isEmpty()) {
                draft.source = "";
            } else {
                String current = sourceField.getValue().trim();
                int index = names.indexOf(current);
                draft.source = names.get((index + 1 + names.size()) % names.size());
            }
            sourceField.setValue(draft.source);
            syncButtons();
        }).bounds(left + panelWidth - 72, row, 72, 20).build());

        row += 34;
        selectionButton = addRenderableWidget(Button.builder(VpTexts.tr("button.videoplayer.start_selection", "Start Selection"), button -> {
            copyFieldsToDraft();
            editor.beginSelection(draft);
        }).bounds(left, row, 96, 20).build());
        addRenderableWidget(Button.builder(VpTexts.tr("button.videoplayer.clear_selection", "Clear Selection"), button -> {
            editor.clearSelection();
            syncButtons();
        }).bounds(left + 104, row, 96, 20).build());
        confirmButton = addRenderableWidget(Button.builder(VpTexts.tr("button.videoplayer.create", "Create"), button -> {
            copyFieldsToDraft();
            editor.confirm(result -> {
                if (ClientPacketHandler.denied(result)) {
                    button.setMessage(VpTexts.tr("error.videoplayer.permission_denied", "Permission denied"));
                    return;
                }
                if (result != null && result.status() == RequestResultStatus.OK) onClose();
            });
            syncButtons();
        }).bounds(left + panelWidth - 96, row, 96, 20).build());

        row += 28;
        addRenderableWidget(Button.builder(VpTexts.tr("button.videoplayer.close", "Close"), button -> onClose()).bounds(left, row, panelWidth, 20).build());

        syncButtons();
        setInitialFocus(nameField);
    }

    @Override
    public void tick() {
        copyFieldsToDraft();
        syncButtons();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int panelWidth = Math.min(320, width - 40);
        int left = (width - panelWidth) / 2;
        int top = Math.max(24, height / 2 - 112);
        int bottom = top + 228;

        context.fill(left - 12, top - 12, left + panelWidth + 12, bottom, 0xCC101010);
        context.drawCenteredString(font, title, width / 2, top - 2, 0xFFFFFFFF);
        context.drawString(font, VpTexts.tr("label.videoplayer.name", "Name"), left, top + 28, 0xFFE0E0E0);
        context.drawString(font, VpTexts.tr("label.videoplayer.type", "Type"), left, top + 56, 0xFFE0E0E0);
        context.drawString(font, VpTexts.tr("label.videoplayer.area", "Area"), left, top + 84, 0xFFE0E0E0);
        context.drawString(font, VpTexts.tr("label.videoplayer.mode", "Mode"), left, top + 112, 0xFFE0E0E0);
        context.drawString(font, VpTexts.tr("label.videoplayer.source", "Source"), left, top + 140, 0xFFE0E0E0);

        String points = editor.pointProgress();
        int statusColor = editor.statusError() ? 0xFFFF5555 : 0xFF55FF55;
        context.drawString(font, VpTexts.tr("label.videoplayer.selection_points", "Selection: %s", points), left, top + 172, 0xFFE0E0E0);
        context.drawString(font, editor.status(), left + 72, top + 172, statusColor);

        if (draft.target == VideoCreationEditor.Target.SCREEN && draft.areaName.isEmpty()) {
            context.drawString(font, VpTexts.tr("error.videoplayer.need_area_first", "Enter or create an Area first").withStyle(ChatFormatting.RED), left, top + 190, 0xFFFF5555);
        } else if (draft.target == VideoCreationEditor.Target.SCREEN) {
            context.drawString(font, Component.translatableWithFallback(
                    "hint.videoplayer.select_points",
                    "%1$s points, %2$s undo, press %3$s to return and confirm",
                    editor.leftMouseText(), editor.rightMouseText(), editor.openKeyText()
            ), left, top + 190, 0xFFB0B0B0);
        } else {
            context.drawString(font, VpTexts.tr("hint.videoplayer.area_two_blocks", "Area uses two blocks to create a bounding box"), left, top + 190, 0xFFB0B0B0);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void copyFieldsToDraft() {
        draft.name = nameField == null ? draft.name : nameField.getValue().trim();
        draft.source = sourceField == null ? draft.source : sourceField.getValue().trim();
        editor.draft().copyFrom(draft);
    }

    private void syncButtons() {
        if (nameField != null && !nameField.getValue().equals(draft.name)) {
            draft.name = nameField.getValue().trim();
        }
        if (sourceField != null && !sourceField.getValue().equals(draft.source)) {
            draft.source = sourceField.getValue().trim();
        }
        editor.draft().copyFrom(draft);
        boolean screen = draft.target == VideoCreationEditor.Target.SCREEN;
        targetButton.setMessage(VpTexts.tr("label.videoplayer.type_value", "Type: %s", draft.target.label()));
        areaButton.setMessage(draft.areaName.isEmpty()
                ? VpTexts.tr("label.videoplayer.area_none", "Area: None")
                : VpTexts.tr("label.videoplayer.area_value", "Area: %s", draft.areaName));
        areaButton.active = screen && !editor.areaNames().isEmpty();
        screenModeButton.setMessage(VpTexts.tr("label.videoplayer.mode_value", "Mode: %s", draft.screenMode.label()));
        screenModeButton.active = screen;
        sourceField.visible = screen;
        sourceField.active = screen;
        sourceButton.visible = screen;
        sourceButton.active = screen;
        selectionButton.active = canSelect();
        confirmButton.active = editor.ready() && canSubmit();
    }

    private boolean canSelect() {
        return draft.target == VideoCreationEditor.Target.AREA || editor.areaNames().contains(draft.areaName);
    }

    private boolean canSubmit() {
        return canSelect();
    }

    private String suggestedName() {
        if (draft.target == VideoCreationEditor.Target.AREA) {
            return editor.suggestedAreaName();
        }
        return editor.suggestedScreenName(draft.areaName);
    }
}
