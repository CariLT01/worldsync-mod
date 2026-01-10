package com.worldsync.mixin.client;

import com.worldsync.DownloadWorldScreen;
import com.worldsync.WorldSyncScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SelectWorldScreen.class)
public class SelectWorldScreenMixin extends Screen  {

    protected SelectWorldScreenMixin(Component title) {
        super(title);
    }

    @Inject(at = @At("TAIL"), method = "init")
    private void addWorldSyncButton(CallbackInfo ci) {
        this.addRenderableWidget(Button.builder(Component.literal("Upload World"), (button) -> {
            this.minecraft.setScreen(new WorldSyncScreen(this));
        }).bounds(5, 5, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Download World"), (button) -> {
            this.minecraft.setScreen(new DownloadWorldScreen(this));
        }).bounds(90, 5, 80, 20).build());
    }
}
