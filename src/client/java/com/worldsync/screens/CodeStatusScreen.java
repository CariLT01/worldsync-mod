package com.worldsync.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CodeStatusScreen extends Screen {

    public String code = "00000";
    public Screen parent;

    public CodeStatusScreen(Screen parent) {
        super(Component.literal("[...]"));

        this.parent = parent;


    }

    public void setCode(String status) {
        this.code = status;
    }

    @Override
    protected void init() {

        this.addRenderableWidget(
                Button.builder(Component.literal("Code for this world is: " + this.code), (button) -> {

                }).bounds(this.width / 2 - 100, this.height / 6 + 108, 200, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Back"), (button) -> {
                    this.minecraft.setScreen(this.parent);
                }).bounds(this.width / 2 - 100, this.height / 6 + 168, 200, 20).build()
        );



    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);


    }

}
