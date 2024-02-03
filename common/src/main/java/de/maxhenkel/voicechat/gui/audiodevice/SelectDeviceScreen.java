package de.maxhenkel.voicechat.gui.audiodevice;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.maxhenkel.configbuilder.entry.ConfigEntry;
import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.gui.VoiceChatScreenBase;
import de.maxhenkel.voicechat.gui.widgets.ListScreenBase;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.List;

public abstract class SelectDeviceScreen extends ListScreenBase {

    protected static final ResourceLocation TEXTURE = new ResourceLocation(Voicechat.MODID, "textures/gui/gui_audio_devices.png");
    protected static final Component BACK = new TranslatableComponent("message.voicechat.back");

    protected static final int HEADER_SIZE = 16;
    protected static final int FOOTER_SIZE = 32;
    protected static final int UNIT_SIZE = 18;


    @Nullable
    protected Screen parent;
    protected AudioDeviceList deviceList;
    protected Button back;
    protected int units;

    public SelectDeviceScreen(Component title, @Nullable Screen parent) {
        super(title, 236, 0);
        this.parent = parent;
    }

    public abstract List<String> getDevices();

    public abstract ResourceLocation getIcon();

    public abstract Component getEmptyListComponent();

    public abstract ConfigEntry<String> getConfigEntry();

    @Override
    protected void init() {
        super.init();
        guiLeft = guiLeft + 2;
        guiTop = 32;
        int minUnits = Mth.ceil((float) (AudioDeviceList.CELL_HEIGHT + 4) / (float) UNIT_SIZE);
        units = Math.max(minUnits, (height - HEADER_SIZE - FOOTER_SIZE - guiTop * 2) / UNIT_SIZE);
        ySize = HEADER_SIZE + units * UNIT_SIZE + FOOTER_SIZE;

        if (deviceList != null) {
            deviceList.updateSize(width, units * UNIT_SIZE, guiTop + HEADER_SIZE);
        } else {
            deviceList = new AudioDeviceList(width, units * UNIT_SIZE, guiTop + HEADER_SIZE).setIcon(getIcon()).setConfigEntry(getConfigEntry());
        }
        addWidget(deviceList);

        back = new Button(guiLeft + 7, guiTop + ySize - 20 - 7, xSize - 14, 20, BACK, button -> {
            minecraft.setScreen(parent);
        });
        addRenderableWidget(back);

        deviceList.setAudioDevices(getDevices());
    }


    @Override
    public void renderBackground(PoseStack poseStack, int mouseX, int mouseY, float delta) {
        if (isIngame()) {
            RenderSystem.setShaderTexture(0, TEXTURE);
            blit(poseStack, guiLeft, guiTop, 0, 0, xSize, HEADER_SIZE);
            for (int i = 0; i < units; i++) {
                blit(poseStack, guiLeft, guiTop + HEADER_SIZE + UNIT_SIZE * i, 0, HEADER_SIZE, xSize, UNIT_SIZE);
            }
            blit(poseStack, guiLeft, guiTop + HEADER_SIZE + UNIT_SIZE * units, 0, HEADER_SIZE + UNIT_SIZE, xSize, FOOTER_SIZE);
            blit(poseStack, guiLeft + 10, guiTop + HEADER_SIZE + 6 - 2, xSize, 0, 12, 12);
        }
    }

    @Override
    public void renderForeground(PoseStack poseStack, int mouseX, int mouseY, float delta) {
        font.draw(poseStack, title, width / 2 - font.width(title) / 2, guiTop + 5, isIngame() ? VoiceChatScreenBase.FONT_COLOR : ChatFormatting.WHITE.getColor());
        if (!deviceList.isEmpty()) {
            deviceList.render(poseStack, mouseX, mouseY, delta);
        } else {
            drawCenteredString(poseStack, font, getEmptyListComponent(), width / 2, guiTop + HEADER_SIZE + (units * UNIT_SIZE) / 2 - font.lineHeight / 2, -1);
        }
    }

}
