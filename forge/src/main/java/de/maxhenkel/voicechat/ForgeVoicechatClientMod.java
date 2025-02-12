package de.maxhenkel.voicechat;

import de.maxhenkel.voicechat.config.ConfigMigrator;
import de.maxhenkel.voicechat.gui.VoiceChatSettingsScreen;
import de.maxhenkel.voicechat.gui.onboarding.OnboardingManager;
import de.maxhenkel.voicechat.intercompatibility.ClientCompatibilityManager;
import de.maxhenkel.voicechat.intercompatibility.ForgeClientCompatibilityManager;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class ForgeVoicechatClientMod extends VoicechatClient {

    public ForgeVoicechatClientMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(((ForgeClientCompatibilityManager) ClientCompatibilityManager.INSTANCE)::onRegisterKeyBinds);
    }

    public void clientSetup(FMLClientSetupEvent event) {
        initializeClient();
        MinecraftForge.EVENT_BUS.register(ClientCompatibilityManager.INSTANCE);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> {
            if (OnboardingManager.isOnboarding()) {
                return OnboardingManager.getOnboardingScreen(parent);
            }
            return new VoiceChatSettingsScreen(parent);
        }));
    }

    @Override
    public void initializeConfigs() {
        super.initializeConfigs();
        ConfigMigrator.migrateClientConfig();
    }

}
