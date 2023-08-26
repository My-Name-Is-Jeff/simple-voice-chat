package de.maxhenkel.voicechat.voice.client;

import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.api.ClientVoicechatSocket;
import de.maxhenkel.voicechat.api.RawUdpPacket;
import de.maxhenkel.voicechat.debug.CooldownTimer;
import de.maxhenkel.voicechat.debug.VoicechatUncaughtExceptionHandler;
import de.maxhenkel.voicechat.intercompatibility.ClientCompatibilityManager;
import de.maxhenkel.voicechat.net.IntegratedSocketAddress;
import de.maxhenkel.voicechat.plugins.PluginManager;
import de.maxhenkel.voicechat.plugins.impl.ClientIntegratedVoicechatSocketImpl;
import de.maxhenkel.voicechat.voice.common.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

public class ClientVoicechatConnection extends Thread {

    private ClientVoicechat client;
    private final InitializationData data;
    private final ClientVoicechatSocket socket;
    private final InetAddress address;
    private boolean running;
    private boolean authenticated;
    private boolean connected;
    private final AuthThread authThread;
    private long lastKeepAlive;

    public ClientVoicechatConnection(ClientVoicechat client, InitializationData data) throws Exception {
        this.client = client;
        this.data = data;
        this.address = InetAddress.getByName(data.getServerIP());
        if (data.getServerPort() < 0) {
            this.socket = new ClientIntegratedVoicechatSocketImpl();
        } else {
            this.socket = PluginManager.instance().getClientSocketImplementation();
        }
        this.lastKeepAlive = -1;
        this.running = true;
        this.authThread = new AuthThread();
        this.authThread.start();
        setDaemon(true);
        setName("VoiceChatConnectionThread");
        setUncaughtExceptionHandler(new VoicechatUncaughtExceptionHandler());
        this.socket.open();
    }

    public InitializationData getData() {
        return data;
    }

    public InetAddress getAddress() {
        return address;
    }

    public ClientVoicechatSocket getSocket() {
        return socket;
    }

    public boolean isInitialized() {
        return authenticated && connected;
    }

    public void onIntegratedPackets(List<RawUdpPacket> packets) {
        if (!(socket instanceof ClientIntegratedVoicechatSocketImpl)) {
            CooldownTimer.run("socket_not_integrated", () -> {
                Voicechat.LOGGER.warn("Received integrated packets but socket is not using integrated networking");
            });
            return;
        }
        ((ClientIntegratedVoicechatSocketImpl) socket).receivePackets(packets);
    }

    @Override
    public void run() {
        try {
            while (running) {
                NetworkMessage in = NetworkMessage.readPacketClient(socket.read(), this);
                if (in == null) {
                    continue;
                } else if (in.getPacket() instanceof AuthenticateAckPacket) {
                    if (!authenticated) {
                        Voicechat.LOGGER.info("Server acknowledged authentication");
                        authenticated = true;
                    }
                } else if (in.getPacket() instanceof ConnectionCheckAckPacket) {
                    if (authenticated && !connected) {
                        Voicechat.LOGGER.info("Server acknowledged connection check");
                        connected = true;
                        ClientCompatibilityManager.INSTANCE.emitVoiceChatConnectedEvent(this);
                        lastKeepAlive = System.currentTimeMillis();
                    }
                } else if (in.getPacket() instanceof SoundPacket packet) {
                    client.processSoundPacket(packet);
                } else if (in.getPacket() instanceof PingPacket packet) {
                    Voicechat.LOGGER.info("Received ping {}, sending pong...", packet.getId());
                    sendToServer(new NetworkMessage(packet));
                } else if (in.getPacket() instanceof KeepAlivePacket) {
                    lastKeepAlive = System.currentTimeMillis();
                    sendToServer(new NetworkMessage(new KeepAlivePacket()));
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            if (running) {
                Voicechat.LOGGER.error("Failed to process packet from server", e);
            }
        }
    }

    public void close() {
        Voicechat.LOGGER.info("Disconnecting voicechat");
        running = false;

        socket.close();
        authThread.close();
    }

    public boolean isConnected() {
        return running && !socket.isClosed();
    }

    public void sendToServer(NetworkMessage message) throws Exception {
        if (!isConnected()) {
            return; // Ignore sending packets when connection is closed
        }
        if (data.getServerPort() < 0) {
            socket.send(message.writeClient(this), new IntegratedSocketAddress(data.getPlayerUUID()));
        } else {
            socket.send(message.writeClient(this), new InetSocketAddress(address, data.getServerPort()));
        }
    }

    public void checkTimeout() {
        if (lastKeepAlive >= 0 && System.currentTimeMillis() - lastKeepAlive > data.getKeepAlive() * 10L) {
            Voicechat.LOGGER.info("Connection timeout");
            disconnect();
        }
    }

    public void disconnect() {
        ClientCompatibilityManager.INSTANCE.emitVoiceChatDisconnectedEvent();
    }

    private class AuthThread extends Thread {

        private boolean running;
        private int authLogMessageCount;
        private int validateLogMessageCount;

        public AuthThread() {
            this.running = true;
            setDaemon(true);
            setName("VoiceChatAuthenticationThread");
            setDefaultUncaughtExceptionHandler(new VoicechatUncaughtExceptionHandler());
        }

        @Override
        public void run() {
            while (running) {
                if (authenticated && connected) {
                    break;
                }
                if (!authenticated) {
                    validateLogMessageCount = 0;
                    try {
                        if (authLogMessageCount < 10) {
                            Voicechat.LOGGER.info("Trying to authenticate voice chat connection");
                            authLogMessageCount++;
                        } else if (authLogMessageCount == 10) {
                            Voicechat.LOGGER.warn("Trying to authenticate voice chat connection (this message will not be logged again)");
                            authLogMessageCount++;
                        }
                        sendToServer(new NetworkMessage(new AuthenticatePacket(data.getPlayerUUID(), data.getSecret())));
                    } catch (Exception e) {
                        if (!socket.isClosed()) {
                            Voicechat.LOGGER.error("Failed to authenticate voice chat connection: {}", e.getMessage());
                        }
                    }
                } else {
                    authLogMessageCount = 0;
                    try {
                        if (validateLogMessageCount < 10) {
                            Voicechat.LOGGER.info("Trying to validate voice chat connection");
                            validateLogMessageCount++;
                        } else if (validateLogMessageCount == 10) {
                            Voicechat.LOGGER.warn("Trying to validate voice chat connection (this message will not be logged again)");
                            validateLogMessageCount++;
                        }

                        sendToServer(new NetworkMessage(new ConnectionCheckPacket()));
                    } catch (Exception e) {
                        if (!socket.isClosed()) {
                            Voicechat.LOGGER.error("Failed to validate voice chat connection: {}", e.getMessage());
                        }
                    }
                }

                Utils.sleep(1000);
            }
        }

        public void close() {
            running = false;
        }
    }

}