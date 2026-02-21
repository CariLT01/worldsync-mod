package com.worldsync;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class DownloadWorldScreen extends Screen {

    private final Screen parent;

    private int currentSelectedId = -1;

    public DownloadWorldScreen(Screen parent) {
        super(Component.literal("World Sync Menu"));
        this.parent = parent;
    }

    @Override
    protected void init() {

        File savesFolder = Minecraft.getInstance().gameDirectory.toPath().resolve("saves").toFile();

        File[] items = savesFolder.listFiles();

        List<String> itemsString = new ArrayList<>();
        Map<String, File> worldNameToFileMap = new HashMap<>();


        assert items != null;
        for (File world : items) {
            itemsString.add(world.getName());
            worldNameToFileMap.put(world.getName(), world);
        }

        LinearLayout layout = LinearLayout.vertical().spacing(8);
        layout.defaultCellSetting().alignHorizontallyCenter();


        EditBox worldId = getEditBox();


        layout.addChild(worldId);

        layout.arrangeElements();
        layout.setPosition(this.width / 2 - layout.getWidth() / 2, this.height / 3);
        layout.visitWidgets(this::addRenderableWidget);



        this.addRenderableWidget(
                Button.builder(Component.literal("Sync World"), (button) -> {

                    WorkerStatusScreen progressScreen = new WorkerStatusScreen();
                    progressScreen.setTaskName("Downloading world");
                    minecraft.execute(() -> {
                        minecraft.setScreen(progressScreen);
                    });

                    new Thread(() -> {

                        if (this.currentSelectedId == -1) return;

                        WorldDownloader wd = new WorldDownloader();

                        try {

                            List<String> issues = wd.downloadWorld(this.currentSelectedId, progressScreen);
                            MessageScreen msgScreen = new MessageScreen(this);
                            msgScreen.setMessage("World has been downloaded");
                            msgScreen.setIssues(issues);
                            minecraft.execute(() -> {
                                minecraft.setScreen(msgScreen);
                            });

                        } catch (Exception e) {
                            MessageScreen msgScreen = new MessageScreen(this);
                            msgScreen.setMessage(String.format("An error occurred while trying to download the world: %s", e.getMessage()));
                            minecraft.execute(() -> {
                                minecraft.setScreen(msgScreen);
                            });
                            throw new RuntimeException(e);
                        }

                    }).start();


                }).bounds(this.width / 2 - 100, this.height / 6 + 140, 200, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("Back"), (button) -> {
                    this.minecraft.setScreen(this.parent);
                }).bounds(this.width / 2 - 100, this.height / 6 + 168, 200, 20).build()
        );
    }

    private @NotNull EditBox getEditBox() {
        EditBox worldId = new EditBox(this.font, this.width / 2 - 100, 100, 200, 20, Component.literal("Existing World ID"));
        worldId.setValue("0");
        worldId.setResponder(value -> {
            // Remove non-numeric characters
            String numericValue = value.replaceAll("[^\\d]", "");
            if (!numericValue.equals(value)) {
                // If the input changed (non-digits removed), update the EditBox
                worldId.setValue(numericValue);
            }

            // Update the currentSelectedId safely
            if (!numericValue.isEmpty()) {
                try {
                    this.currentSelectedId = Integer.parseInt(numericValue);
                } catch (NumberFormatException e) {
                    this.currentSelectedId = -1; // fallback
                }
            } else {
                this.currentSelectedId = -1; // empty input means 0
            }
        });
        return worldId;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);
    }
}
