package de.maxhenkel.voicechat.resourcepacks;

import com.google.common.collect.ImmutableSet;
import de.maxhenkel.voicechat.Voicechat;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class VoiceChatResourcePack extends AbstractPackResources implements Pack.ResourcesSupplier {

    public VoiceChatResourcePack(String id, Component name) {
        super(new PackLocationInfo(id, name, PackSource.BUILT_IN, Optional.empty()));
    }

    public Pack toPack() {
        int packVersion = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        Pack.Metadata meta = Pack.readPackMetadata(location(), this, packVersion);
        if (meta == null) {
            throw new IllegalStateException("Could not find builtin resource pack info");
        }
        return Pack.readMetaAndCreate(location(), this, PackType.CLIENT_RESOURCES, new PackSelectionConfig(false, Pack.Position.TOP, false));
    }

    private String getPath() {
        return "/packs/" + packId() + "/";
    }

    @Nullable
    private InputStream get(String name) {
        return Voicechat.class.getResourceAsStream(getPath() + name);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... strings) {
        return getResource(String.join("/", strings));
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation resourceLocation) {
        return getRootResource(packType.getDirectory(), resourceLocation.getNamespace(), resourceLocation.getPath());
    }

    @Nullable
    private IoSupplier<InputStream> getResource(String path) {
        InputStream resourceAsStream = get(path);
        if (resourceAsStream == null) {
            return null;
        }
        return () -> resourceAsStream;
    }

    @Override
    public void listResources(PackType type, String namespace, String prefix, ResourceOutput resourceOutput) {
        try {
            URL url = Voicechat.class.getResource(getPath());
            if (url == null) {
                return;
            }
            Path namespacePath = Paths.get(url.toURI()).resolve(type.getDirectory()).resolve(namespace);
            Path resPath = namespacePath.resolve(prefix);

            if (!Files.exists(resPath)) {
                return;
            }

            try (Stream<Path> files = Files.walk(resPath)) {
                files.filter(path -> !Files.isDirectory(path)).forEach(path -> {
                    ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath(namespace, convertPath(path).substring(convertPath(namespacePath).length() + 1));
                    resourceOutput.accept(resourceLocation, getResource(type, resourceLocation));
                });
            }
        } catch (Exception e) {
            Voicechat.LOGGER.error("Failed to list builtin pack resources", e);
        }
    }

    private static String convertPath(Path path) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < path.getNameCount(); i++) {
            stringBuilder.append(path.getName(i));
            if (i < path.getNameCount() - 1) {
                stringBuilder.append("/");
            }
        }
        return stringBuilder.toString();
    }

    @Override
    public Set<String> getNamespaces(PackType packType) {
        if (packType == PackType.CLIENT_RESOURCES) {
            return ImmutableSet.of(Voicechat.MODID);
        }
        return ImmutableSet.of();
    }

    @Override
    public void close() {

    }


    @Override
    public PackResources openPrimary(PackLocationInfo packLocationInfo) {
        return this;
    }

    @Override
    public PackResources openFull(PackLocationInfo packLocationInfo, Pack.Metadata metadata) {
        return this;
    }
}
