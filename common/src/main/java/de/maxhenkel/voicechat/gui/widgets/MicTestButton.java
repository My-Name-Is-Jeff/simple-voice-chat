package de.maxhenkel.voicechat.gui.widgets;

import com.mojang.blaze3d.vertex.PoseStack;
import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.VoicechatClient;
import de.maxhenkel.voicechat.debug.VoicechatUncaughtExceptionHandler;
import de.maxhenkel.voicechat.voice.client.*;
import de.maxhenkel.voicechat.voice.client.speaker.*;
import de.maxhenkel.voicechat.voice.common.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class MicTestButton extends AbstractButton {

    private static final Component TEST = Component.translatable("message.voicechat.mic_test");
    private static final Component DISABLE_TEST = Component.literal("X");
    private static final Component TEST_UNAVAILABLE = Component.translatable("message.voicechat.mic_test_unavailable").withStyle(ChatFormatting.RED);

    private boolean micActive;
    @Nullable
    private VoiceThread voiceThread;
    private final MicListener micListener;
    @Nullable
    private final ClientVoicechat client;

    public MicTestButton(int xIn, int yIn, int widthIn, int heightIn, MicListener micListener) {
        super(xIn, yIn, widthIn, heightIn, TEST);
        this.micListener = micListener;
        this.client = ClientManager.getClient();
        active = client == null || client.getSoundManager() != null;
    }

    @Override
    public void render(PoseStack matrixStack, int x, int y, float partialTicks) {
        super.render(matrixStack, x, y, partialTicks);
        if (visible && voiceThread != null) {
            voiceThread.updateLastRender();
        }
    }

    public void setMicActive(boolean micActive) {
        this.micActive = micActive;
        updateText();
    }

    private void updateText() {
        if (micActive) {
            setMessage(DISABLE_TEST);
        } else {
            setMessage(TEST);
        }
    }

    @Nullable
    public Component getHoverText() {
        if (!active) {
            return TEST_UNAVAILABLE;
        }
        return null;
    }

    @Override
    public void onPress() {
        setMicActive(!micActive);
        if (micActive) {
            close();
            try {
                voiceThread = new VoiceThread(e -> {
                    setMicActive(false);
                    active = false;
                    Voicechat.LOGGER.error("Microphone error", e);
                });
                voiceThread.start();
            } catch (Exception e) {
                setMicActive(false);
                active = false;
                Voicechat.LOGGER.error("Microphone error", e);
            }
        } else {
            close();
        }
        updateText();
    }

    private void close() {
        if (voiceThread != null) {
            voiceThread.close();
            voiceThread = null;
        }
    }

    public void stop() {
        close();
        setMicActive(false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }

    private class VoiceThread extends Thread {

        private final MicActivator micActivator;
        private final Speaker speaker;
        private boolean running;
        private long lastRender;
        private MicThread micThread;
        private boolean usesOwnMicThread;
        @Nullable
        private SoundManager ownSoundManager;

        public VoiceThread(Consumer<MicrophoneException> onMicError) throws SpeakerException {
            this.running = true;
            setDaemon(true);
            setName("VoiceTestingThread");
            setUncaughtExceptionHandler(new VoicechatUncaughtExceptionHandler());

            micActivator = new MicActivator();

            micThread = client != null ? client.getMicThread() : null;
            if (micThread == null) {
                micThread = new MicThread(client, null, onMicError);
                usesOwnMicThread = true;
            } else {
                micThread.getError(onMicError);
            }

            SoundManager soundManager;
            if (client == null) {
                soundManager = new SoundManager(VoicechatClient.CLIENT_CONFIG.speaker.get());
                ownSoundManager = soundManager;
            } else {
                soundManager = client.getSoundManager();
            }

            if (soundManager == null) {
                throw new SpeakerException("No sound manager");
            }

            speaker = SpeakerManager.createSpeaker(soundManager, null);

            updateLastRender();
            setMicLocked(true);
        }

        @Override
        public void run() {
            while (running) {
                if (System.currentTimeMillis() - lastRender > 500L) {
                    break;
                }
                short[] buff = micThread.pollMic();
                if (buff == null) {
                    continue;
                }

                micListener.onMicValue(Utils.dbToPerc(Utils.getHighestAudioLevel(buff)));

                if (VoicechatClient.CLIENT_CONFIG.microphoneActivationType.get().equals(MicrophoneActivationType.VOICE)) {
                    if (micActivator.push(buff, a -> {
                    })) {
                        play(buff);
                    }
                } else {
                    micActivator.stopActivating();
                    play(buff);
                }

            }
            speaker.close();
            setMicLocked(false);
            micListener.onMicValue(0D);
            if (usesOwnMicThread) {
                micThread.close();
            }
            if (ownSoundManager != null) {
                ownSoundManager.close();
            }
            setMicActive(false);
            Voicechat.LOGGER.info("Mic test audio channel closed");
        }

        private void play(short[] buff) {
            speaker.play(buff, VoicechatClient.CLIENT_CONFIG.voiceChatVolume.get().floatValue(), null);
        }

        public void updateLastRender() {
            lastRender = System.currentTimeMillis();
        }

        private void setMicLocked(boolean locked) {
            micThread.setMicrophoneLocked(locked);
        }

        public void close() {
            if (!running) {
                return;
            }
            Voicechat.LOGGER.info("Stopping mic test audio channel");
            running = false;
            try {
                join();
            } catch (InterruptedException e) {
                Voicechat.LOGGER.warn("Failed to close microphone", e);
            }
        }
    }

    public interface MicListener {
        void onMicValue(double percentage);
    }
}
