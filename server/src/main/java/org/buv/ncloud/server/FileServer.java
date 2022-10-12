package org.buv.ncloud.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import lombok.extern.slf4j.Slf4j;
import org.buv.ncloud.server.serial.FileHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
public class FileServer {

    public static void main(String[] args) {
        EventLoopGroup auth = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(auth, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline().addLast(
                                    new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                                    new ObjectEncoder(),
                                    new FileHandler()
                            );
                        }
                    });
            ChannelFuture channelFuture = bootstrap.bind(8189).sync();
            log.debug("Server is ready on port: " + 8189);
            //подключаем базу данных/либо заводим новую базу
            serverDirCheck();
            AuthServer.connect();
            channelFuture.channel().closeFuture().sync(); // block
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            auth.shutdownGracefully();
            worker.shutdownGracefully();
            AuthServer.disconnect();
        }
    }

    private static void serverDirCheck() {
        File serverDir = Paths.get("server_files").toFile();
        if(!serverDir.exists()){
            try {
                Files.createDirectory(serverDir.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
