package com.ruinscraft.panilla.api.io;

import com.ruinscraft.panilla.api.IPanilla;
import com.ruinscraft.panilla.api.IPanillaPlayer;
import com.ruinscraft.panilla.api.io.dplx.PacketDecompressorDplx;
import com.ruinscraft.panilla.api.io.dplx.PacketInspectorDplx;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.NoSuchElementException;

public interface IPlayerInjector {

    String HANDLER_PANILLA_INSPECTOR = "panilla_inspector";

    Channel getPlayerChannel(IPanillaPlayer player);

    int getCompressionLevel();

    ByteToMessageDecoder getDecompressor();

    default String getDecompressorHandlerName() {
        return "decompress";
    }

    default String getPacketHandlerName() {
        return "packet_handler";
    }

    default void register(IPanilla panilla, IPanillaPlayer player) {
        Channel pChannel = getPlayerChannel(player);

        if (pChannel == null) {
            return;
        }

        /* Replace Minecraft packet decompressor */
        ChannelHandler minecraftDecompressor = pChannel.pipeline().get(getDecompressorHandlerName());

        if (minecraftDecompressor != null && !(minecraftDecompressor instanceof PacketDecompressorDplx)) {
            PacketDecompressorDplx packetDecompressor = new PacketDecompressorDplx(panilla, player);
            pChannel.pipeline().replace(getDecompressorHandlerName(), getDecompressorHandlerName(), packetDecompressor);
        }

        /* Inject packet inspector */
        ChannelHandler minecraftHandler = pChannel.pipeline().get(getPacketHandlerName());

        if (minecraftHandler != null && !(minecraftHandler instanceof PacketInspectorDplx)) {
            PacketInspectorDplx packetInspector = new PacketInspectorDplx(panilla, player);
            pChannel.pipeline().addBefore(getPacketHandlerName(), HANDLER_PANILLA_INSPECTOR, packetInspector);
        }
    }

    default void unregister(final IPanillaPlayer player) {
        Channel pChannel = getPlayerChannel(player);

        if (pChannel == null) {
            return;
        }

        /* Replace Panilla packet decompressor with the default */
        ChannelHandler panillaDecompressor = pChannel.pipeline().get(getDecompressorHandlerName());

        if (panillaDecompressor != null && panillaDecompressor instanceof PacketDecompressorDplx) {
            ByteToMessageDecoder minecraftDecompressor = getDecompressor();

            try {
                pChannel.pipeline().replace(getDecompressorHandlerName(), getDecompressorHandlerName(), minecraftDecompressor);
            } catch (NoSuchElementException e) {
                // We can safely ignore this. Even with the instanceof check, depending on circumstance, this can still happen
            }
        }

        /* Remove packet inspector */
        ChannelHandler panillaHandler = pChannel.pipeline().get(HANDLER_PANILLA_INSPECTOR);

        if (panillaHandler != null && panillaHandler instanceof PacketInspectorDplx) {
            try {
                pChannel.pipeline().remove(panillaHandler);
            } catch (NoSuchElementException e) {
                // We can safely ignore this. Even with the instanceof check, depending on circumstance, this can still happen
            }
        }
    }

}
