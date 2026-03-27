package com.worldsync.screens.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class IssuesListWidget extends ObjectSelectionList<IssuesListWidget.IssueEntry> {

    public IssuesListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    public void addIssue(String issue) {
        this.addEntry(new IssueEntry(issue));
    }

    @Override
    public int getRowWidth() {
        return this.width - 20;
    }

    public static class IssueEntry extends ObjectSelectionList.Entry<IssueEntry> {
        private final String text;

        public IssueEntry(String text) {
            this.text = text;
        }

        @Override
        public @NotNull Component getNarration() {
            return Component.literal(text);
        }


        // 3. Override the standard 'render' method and use the provided 'top' and 'left' arguments
        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int entryWidth, int entryHeight, boolean isSelected, float partialTicks) {
            // In 1.21 renderContent, (0,0) is the top-left of the slot.
            // We use '0' for X and '2' for Y to center it slightly in the 20px tall row.
            graphics.text(Minecraft.getInstance().font, this.text, 5, 2, 0xFFFF5555, false);
        }
    }
}