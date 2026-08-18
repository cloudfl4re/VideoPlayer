package com.github.squi2rel.vp.creation;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

class FilteredEditBox extends EditBox {
    private Predicate<String> filter = value -> true;

    FilteredEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    void setFilter(Predicate<String> filter) {
        this.filter = Objects.requireNonNull(filter, "filter");
    }

    @Override
    public void setValue(String value) {
        if (filter.test(value)) {
            super.setValue(value);
        }
    }

    @Override
    public void insertText(String insertion) {
        if (TextInputFilter.accepts(filter, getValue(), getCursorPosition(), getHighlighted(), insertion)) {
            super.insertText(insertion);
        }
    }
}
