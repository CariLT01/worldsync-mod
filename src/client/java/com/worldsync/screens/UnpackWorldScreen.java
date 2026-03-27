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
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.nfd.NativeFileDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.lwjgl.util.nfd.NativeFileDialog.*;

public class UnpackWorldScreen extends Screen {

    private final Screen parent;
    private final Logger LOGGER = LoggerFactory.getLogger(WorldSync.MOD_ID);

    private File currentSelectedFile;

    private StringWidget selectedFileLabel;

    public UnpackWorldScreen(Screen parent) {
        super(Component.literal("World Sync Menu"));
        this.parent = parent;
    }

    private static String pickFile() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer outPath = stack.mallocPointer(1);
            int result = NFD_OpenDialog(outPath, null, (CharSequence) null);
            if (result == NFD_OKAY) {
                return outPath.getStringUTF8(0);
            }
            if (result == NFD_CANCEL) {
                return null;
            }
            throw new IllegalStateException(NativeFileDialog.NFD_GetError());
        } finally {
            NativeFileDialog.NFD_Quit();
        }
    }

    private String getCurrentSelectedWorldString() {
        return currentSelectedFile == null ? "None selected" : currentSelectedFile.getName();
    }

    @Override
    protected void init() {

        LinearLayout layout = LinearLayout.vertical().spacing(8);
        layout.defaultCellSetting().alignHorizontallyCenter();

        // description
        StringWidget descriptionLabel = new StringWidget(Component.literal("Select a ZIP file to unpack"), this.font);
        this.selectedFileLabel = new StringWidget(Component.literal(String.format("Selected file: %s", this.getCurrentSelectedWorldString())), this.font);

        layout.addChild(descriptionLabel);
        layout.addChild(this.selectedFileLabel);
        layout.addChild(Button.builder(Component.literal("Select File"), (button) -> {
            new Thread(() -> {
                String filePath = pickFile();

                if (filePath != null) {
                    this.currentSelectedFile = new File(filePath);
                    this.selectedFileLabel.setMessage(Component.literal(String.format("Selected file: %s", this.getCurrentSelectedWorldString())));
                }
            }).start();

        }).bounds(this.width / 2 - 100, this.height / 6 + 140, 200, 20).build());


        layout.arrangeElements();
        layout.setPosition(this.width / 2 - layout.getWidth() / 2, this.height / 3);
        layout.visitWidgets(this::addRenderableWidget);



        this.addRenderableWidget(

                Button.builder(Component.literal("Unpack World"), (button) -> {
                    if (this.currentSelectedFile == null) return;
                    new Thread(() -> {
                        try {
                            if (!this.currentSelectedFile.getName().endsWith(".zip")) {
                                throw new IllegalArgumentException("Selected file is not a .ZIP");
                            }


                            File savesFolder = Minecraft.getInstance().gameDirectory.toPath().resolve("saves").toFile();
                            Path worldFolderPath = savesFolder.toPath().resolve(this.currentSelectedFile.getName());

                            WorkerStatusScreen progressScreen = new WorkerStatusScreen();
                            progressScreen.setTaskName("Unpack world");
                            this.minecraft.execute(() -> {
                                this.minecraft.setScreen(progressScreen);
                            });


                            WorldZipper.unzipFolder(this.currentSelectedFile.toPath(), worldFolderPath, progressScreen);

                            MessageScreen messageScreen = new MessageScreen(this.parent);
                            messageScreen.setMessage("World unpacked successfully");
                            this.minecraft.execute(() -> {
                                this.minecraft.setScreen(messageScreen);
                            });
                            LOGGER.info("World successfully unpacked");
                        } catch (Exception e) {
                            LOGGER.error("Failed to unpack world: ", e);
                            MessageScreen messageScreen = new MessageScreen(this.parent);
                            messageScreen.setMessage("Failed to unpack the world");
                            messageScreen.setIssues(List.of(e.getMessage()));
                            this.minecraft.execute(() -> {
                                this.minecraft.setScreen(messageScreen);
                            });
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
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);
    }
}
