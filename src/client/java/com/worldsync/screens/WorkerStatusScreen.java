package com.worldsync.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


public class WorkerStatusScreen extends Screen {

    private String overallStatus = "";
    private String taskName = "";
    private final AtomicInteger frameCounter = new AtomicInteger(0);

    private static final String[] loadingCharacters = {"/", "-", "\\", "|"};

    private final Map<String, String> workerStatus = new HashMap<>();

    private final AtomicInteger failuresCounter = new AtomicInteger(0);

    public WorkerStatusScreen() {
        super(Component.literal("Executing tasks..."));
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public void setWorkerStatus(String workerName, String workerStatus) {
        this.workerStatus.put(workerName, workerStatus);
    }

    public void setFailuresCounter(int failuresCounter) {
        this.failuresCounter.set(failuresCounter);
    }

    public void incrementFailuresCounter() {
        this.failuresCounter.getAndIncrement();
    }

    @Override
    protected void init() {
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {

        frameCounter.getAndIncrement();

        guiGraphics.centeredText(this.font, this.taskName, this.width / 2, 40, 0xFFFFFFFF);

        guiGraphics.centeredText(this.font, this.overallStatus, this.width / 2, 80, 0xAAAAAAFF);

        int c = 0;
        int loadingAccumulated = (int) Math.floor((float) frameCounter.get() / 15);
        for (Map.Entry<String, String> entry : this.workerStatus.entrySet()) {
            guiGraphics.centeredText(this.font, String.format(
                    "> [%s] %s %s",
                    entry.getKey(),
                    entry.getValue(),
                    loadingCharacters[(loadingAccumulated + c) % 4]
            ), this.width / 2, 120 + c * 24, 0xAAAAAAFF);
            c++;
        }

        if (failuresCounter.get() > 0) {
            guiGraphics.centeredText(this.font, String.format("%s failure(s)", failuresCounter.get()), this.width / 2, 460, 0xFF0000FF);
        }

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);


    }
}
