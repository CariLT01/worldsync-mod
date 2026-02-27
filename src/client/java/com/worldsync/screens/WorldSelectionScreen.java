package com.worldsync.screens;

import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WorldSelectionScreen extends Screen {
    private final Screen parent;

    public WorldSelectionScreen(Screen parent) {
        super(Component.literal("Select a world"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        LinearLayout layout = LinearLayout.vertical().spacing(8);
    }
}
