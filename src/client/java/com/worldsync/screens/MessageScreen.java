package com.worldsync.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class MessageScreen extends Screen {

    public String message = "No message";
    public Screen parent;

    private float scrollX = 0;
    private float scrollY = 0;

    public List<String> issues = new ArrayList<>();

    public MessageScreen(Screen parent) {
        super(Component.literal("[...]"));

        this.parent = parent;


    }

    public void setMessage(String status) {
        this.message = status;
    }

    @Override
    protected void init() {

        this.addRenderableWidget(
                Button.builder(Component.literal("Back"), (button) -> {
                    this.minecraft.setScreen(this.parent);
                }).bounds(this.width / 2 - 100, this.height / 6 + 168, 200, 20).build()
        );
    }

    public void setIssues(List<String> issues) {
        this.issues.addAll(issues);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {

        guiGraphics.centeredText(this.font, this.message, this.width / 2, 40, 0xFFFFFFFF);

        if (!issues.isEmpty()) {
            guiGraphics.centeredText(this.font, "Errors occurred while executing tasks: ", this.width / 2, 60, 0xFFFFFFFF);
        }


        int top = 100;
        int left = 40;
        int width = this.width - left * 2;
        int height = 360;

        // fill box
        // guiGraphics.fill(top, left, width, height, 0xFF000000);


        int lineHeight = 12;

        int lineCounter = 0;
        for (String line : issues) {
            List<FormattedCharSequence> lines = this.font.split(Component.literal(line), width);
            for (FormattedCharSequence charSequence : lines) {
                guiGraphics.centeredText(this.font, charSequence, left, top + lineHeight * lineCounter, 0xFFFF0000);
                lineCounter++;
            }
        }

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);


    }

}
