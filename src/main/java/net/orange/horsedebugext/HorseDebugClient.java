package net.orange.horsedebugext;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public class HorseDebugClient implements ClientModInitializer, HudRenderCallback, BuildAPI, WorldRenderEvents.AfterEntities {

    private final HorseDebugMain mod;

    public HorseDebugClient() { this.mod = HorseDebugMain.registerAPI(this); }
    public String getAPIName() { return "Fabric"; }


    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(this);
        WorldRenderEvents.AFTER_ENTITIES.register(this);
        ClientTickEvents.END_CLIENT_TICK.register(client -> mod.onKey());
        mod.setup();
    }

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        mod.renderOverlay(drawContext, tickCounter);
    }

    @Override
    public void afterEntities(WorldRenderContext context) {
        mod.renderWorld(context.world().getEntities(), context.matrixStack(), context.camera(), context.consumers());
    }
}
