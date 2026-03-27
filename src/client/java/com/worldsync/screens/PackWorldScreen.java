package com.worldsync.screens;

import com.worldsync.*;
import com.worldsync.functions.WorldZipper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class PackWorldScreen extends Screen {

    private final Screen parent;
    private final Logger LOGGER = LoggerFactory.getLogger(WorldSync.MOD_ID);

    private String currentSelectedWorld = null;



    public PackWorldScreen(Screen parent) {
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

        // description
        StringWidget descriptionLabel = new StringWidget(Component.literal("This will bundle your world into a .zip file"), this.font);
        StringWidget descriptionLabel2 = new StringWidget(Component.literal("It will not automatically upload the world for you."), this.font);

        layout.addChild(descriptionLabel);
        layout.addChild(descriptionLabel2);


        layout.addChild(
                CycleButton.<String>builder(new Function<String, Component>() {
                            @Override
                            public Component apply(String value) {
                                return Component.literal(value);
                            }
                        }, "None")
                        .withValues(itemsString)
                        .create(this.width / 2 - 100, 60, 200, 20, Component.literal("Chosen World to Pack"), (button, value) -> {
                            // Something

                            this.currentSelectedWorld = value;
                        })
        );

        layout.arrangeElements();
        layout.setPosition(this.width / 2 - layout.getWidth() / 2, this.height / 3);
        layout.visitWidgets(this::addRenderableWidget);



        this.addRenderableWidget(
                Button.builder(Component.literal("Pack World"), (button) -> {

                    if (this.currentSelectedWorld == null) return;

                    WorkerStatusScreen progressScreen = new WorkerStatusScreen();
                    progressScreen.setTaskName("Packing World");

                    this.minecraft.setScreen(progressScreen);

                    // get file path

                    File worldFile = worldNameToFileMap.get(this.currentSelectedWorld);
                    if (worldFile == null) return;

                    Path worldPath = worldFile.toPath();
                    // destination

                    Path destinationFolder = Minecraft.getInstance().gameDirectory.toPath().resolve("worldsync").resolve("bundled");
                    try {
                        Files.createDirectories(destinationFolder);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to create destination folder: {}", e.getMessage());
                    }
                    Path zipDestination = destinationFolder.resolve(this.currentSelectedWorld + ".zip");

                    new Thread(() -> {
                        try {
                            WorldZipper.zipFolder(worldPath, zipDestination, progressScreen);
                        } catch (IOException e) {
                            LOGGER.error("Failed to zip: ", e);
                            MessageScreen messageScreen = new MessageScreen(this.parent);
                            messageScreen.setMessage(String.format("Failed to zip: %s", e.getMessage()));

                            Minecraft.getInstance().execute(() -> {
                                Minecraft.getInstance().setScreen(messageScreen);
                            });
                            return;
                        }

                        // open and highlight
                        File destinationFile = zipDestination.toFile();
                        if (!destinationFile.exists()) {
                            LOGGER.error("Failed to open file: does not exist");
                        }

                        try {

                            String os = System.getProperty("os.name").toLowerCase();

                            if (os.contains("win")) {
                                Runtime.getRuntime().exec(new String[]{"explorer.exe", "/select,", destinationFile.getAbsolutePath()});
                            } else if (os.contains("mac")) {
                                Runtime.getRuntime().exec(new String[]{"open", "-R", destinationFile.getAbsolutePath()});
                            } else if (os.contains("nix") || os.contains("nux")) {
                                Runtime.getRuntime().exec(new String[]{"xdg-open", destinationFile.getParent()});
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to open and highlight: ", e);
                        }

                        MessageScreen messageScreen = new MessageScreen(this.parent);
                        messageScreen.setMessage("World zipped");

                        Minecraft.getInstance().execute(() -> {
                            Minecraft.getInstance().setScreen(messageScreen);
                        });
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
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);
    }
}
