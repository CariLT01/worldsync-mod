package com.worldsync;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class MessageScreen extends Screen {

    public String message = "No message";
    public Screen parent;

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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        guiGraphics.drawCenteredString(this.font, this.message, this.width / 2, 40, 0xFFFFFFFF);

        if (!issues.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "Multiple errors occurred while executing tasks: ", this.width / 2, 60, 0xFFFFFFFF);
        }

        int c = 0;
        for (String issue : issues) {
            guiGraphics.drawCenteredString(this.font, issue, this.width / 2, 100 + c * 30, 0xFF0000FF);
            c++;
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);


    }

}
