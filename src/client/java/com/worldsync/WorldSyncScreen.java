package com.worldsync;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class WorldSyncScreen extends Screen {

    private final Screen parent;

    private String currentSelectedWorld = null;
    private int currentSelectedId = -1;

    public WorldSyncScreen(Screen parent) {
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

        layout.addChild(
                Button.builder(Component.literal("Sync..."), (button) -> {

                }).width(200).build()
        );

        layout.addChild(
                CycleButton.<String>builder(new Function<String, Component>() {
                            @Override
                            public Component apply(String value) {
                                return Component.literal(value);
                            }
                        }, "None")
                        .withValues(itemsString)
                        .create(this.width / 2 - 100, 60, 200, 20, Component.literal("Chosen World to Sync"), (button, value) -> {
                            // Something

                            this.currentSelectedWorld = value;
                        })
        );

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


        layout.addChild(worldId);

        layout.arrangeElements();
        layout.setPosition(this.width / 2 - layout.getWidth() / 2, this.height / 3);
        layout.visitWidgets(this::addRenderableWidget);



        this.addRenderableWidget(
                Button.builder(Component.literal("Sync World"), (button) -> {

                    if (this.currentSelectedWorld == null) return;

                    WorldUploader worldUploader = new WorldUploader();

                    Path worldPath = Path.of(Minecraft.getInstance().gameDirectory.getAbsolutePath() + "/saves/" + this.currentSelectedWorld);

                    System.out.println("Uploading path: " + worldPath);

                    ProgressScreen progressScreen = new ProgressScreen(true);

                    this.minecraft.setScreen(progressScreen);

                    new Thread(() -> {
                        try {

                            int gameId = worldUploader.uploadWorld(worldPath.toFile(), this.currentSelectedId, progressScreen);

                            if (this.currentSelectedId == -1) {

                                CodeStatusScreen codeStatusScreen = new CodeStatusScreen(this);
                                codeStatusScreen.setCode(String.valueOf(gameId));

                                this.minecraft.execute(() -> {
                                    this.minecraft.setScreen(codeStatusScreen);
                                });


                            } else {
                                this.minecraft.execute(() -> {
                                    this.minecraft.setScreen(this);
                                });
                            }
                        } catch (Exception e) {

                            this.minecraft.execute(() -> {
                                this.minecraft.setScreen(this);
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

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
    }
}
