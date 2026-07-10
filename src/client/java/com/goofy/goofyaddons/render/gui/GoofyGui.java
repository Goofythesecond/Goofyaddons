package com.goofy.goofyaddons.render.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public class GoofyGui extends Screen {
    private static final int PANEL_WIDTH = 350;
    private static final int PANEL_HEIGHT = 220;
    private static final Identifier HEADER = Identifier.fromNamespaceAndPath("goofyaddons", "textures/gui/header.png");

    private int panelX;
    private int panelY;

    public GoofyGui() {
        super(Component.literal("Goofy Addons"));
    }

    @Override
    protected void init() {
        super.init();
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.fill(panelX, panelY, (panelX + PANEL_WIDTH), (panelY + PANEL_HEIGHT), 0x90252525);


        graphics.fill(panelX, panelY + 60, panelX + PANEL_WIDTH, panelY + 61, 0xFF000000);


        graphics.fill((panelX + 40), (panelY + 60), (panelX + 41), (panelY + PANEL_HEIGHT), 0xFF000000);

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                HEADER,
                panelX + (PANEL_WIDTH - 260) / 2,
                panelY + 8,
                0,
                0,
                260,
                260 * 192 / 1195,
                1195,
                192,
                1195,
                192
        );

        graphics.outline(panelX + (PANEL_WIDTH - 260) / 2, panelY + 8, 260, 260 * 192 / 1195, 0xFF000000);
    }



    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
