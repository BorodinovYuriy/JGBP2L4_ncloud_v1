package org.buv.ncloud.server.serial;

import org.buv.ncloud.constant.Constants;
import org.buv.ncloud.model.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static java.nio.file.StandardOpenOption.APPEND;

@Slf4j
public class FileHandler extends SimpleChannelInboundHandler<CloudMessage> {

    private Path serverDir;
    private Path stop = Path.of("server_files/..");//Организовать под каждого юзера

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    //LIST_MESSAGE
        serverDir = Path.of("server_files");
        ctx.writeAndFlush(new ListMessage(serverDir));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, CloudMessage cloudMessage) throws Exception {
        log.debug("Received: {}", cloudMessage.getType());
    //FILE_MESSAGE (заливка)
        if (cloudMessage instanceof FileMessage fileMessage) {
            saveFileFromUser(fileMessage);
            //обновили лист сервера
            ctx.writeAndFlush(new ListMessage(serverDir));
    //FILE_REQUEST (скачивание)
        } else if (cloudMessage instanceof FileRequest fileRequest) {
            fileRequestResponse(ctx, fileRequest);
            /*System.out.println("fileRequest: " + fileRequest);*/
    //LIST_REQUEST (UPDATE_SERVER_VIEW) -> LIST_MESSAGE
        } else if (cloudMessage instanceof ListRequest listRequest) {
            String selected = listRequest.getSelectedItem();
            Path path = Paths.get(String.valueOf(serverDir),selected).normalize();
            /*Path path = Paths.get(serverDir + "/" + selected).normalize();*/
            if(path.toFile().isDirectory()){
                if(path != stop){
                    serverDir = path;
                    ctx.writeAndFlush(new ListMessage(serverDir));
                }
            }
    //DELETE_REQUEST
        } else if (cloudMessage instanceof DeleteRequest deleteRequest) {
            String filename = String.valueOf(Paths.get(String.valueOf((serverDir)),deleteRequest.getFileToDeleteName()));
            /*String filename = serverDir+"/"+ deleteRequest.getFileToDeleteName();*/
            try {
                Files.delete(Path.of(filename));
                ctx.writeAndFlush(new ListMessage(serverDir));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
    //MOVE_REQUEST
        } else if (cloudMessage instanceof MoveRequest moveRequest) {
            String source = moveRequest.getSource();
            String destination = moveRequest.getDestination();
            System.out.println(source);
            System.out.println(destination);
            if(source != null && destination != null){
                try{
                    Files.move(Paths.get(String.valueOf(serverDir),source)
                            ,Paths.get(String.valueOf(serverDir), destination));
                    /*Files.move(Path.of(serverDir + "/" + source)
                            , Path.of(serverDir + "/" + destination));*/
                }catch (IOException e){
                    e.printStackTrace();
                }
                ctx.writeAndFlush(new ListMessage(serverDir));
            }else System.out.println("null in moveRequest !");
    //CREATE_DIRECTORY
        } else if (cloudMessage instanceof CreateDirOnServerRequest cdosr) {
            createDirectoryOnServer();
            ctx.writeAndFlush(new ListMessage(serverDir));
        }

    }

    private void createDirectoryOnServer() {
        //потом объединить в общий
        try {
            File file = Paths.get(String.valueOf(serverDir),"newDir").toFile();
            if(!file.exists()){
                Files.createDirectory(file.toPath());
            }else {
                for (int i = 0; i < 10000; i++){
                    File dir = Paths.get(String.valueOf(serverDir),"newDir"+i).toFile();
                    if (!dir.exists()){
                        Files.createDirectory(dir.toPath());
                        break;
                    }
                }
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    private void saveFileFromUser(FileMessage fileMessage) throws IOException {
        //переписать в общий метод
        if(!fileMessage.isMultipart()){
            try {
                Files.write(serverDir.resolve(fileMessage.getFileName()), fileMessage.getBytes());
            } catch (IOException e) {
                System.out.println("Upload (little file) error");
                e.printStackTrace();
            }
        }else {
            //тут страховку и поток организовать
            File file = Paths.get(String.valueOf(serverDir),fileMessage.getFileName()).toFile();
            /*File file = new File(serverDir+"/"+fileMessage.getFileName());*/
            if(!file.exists()){
                file.createNewFile();
            }
            try {
                Files.write(file.toPath(), fileMessage.getBytes(), APPEND);
            } catch (IOException e) {
                System.out.println("Upload (big file) error");
                e.printStackTrace();
            }
        }

    }

    private void fileRequestResponse(ChannelHandlerContext ctx, FileRequest fileRequest) throws IOException {
        File file = serverDir.resolve(fileRequest.getFileName()).toFile();
        if(file.exists()){
                long size = Files.size(serverDir.resolve(fileRequest.getFileName()));
            //если размер не превышает FILE_PACK_SIZE
            if (size <= Constants.FILE_PACK_SIZE) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    FileMessage fm = FileMessage.builder()
                            .multipart(false)
                            .fileName(fileRequest.getFileName())
                            .bytes(fis.readAllBytes())
                            .size(size)
                            .build();
                    System.out.println(fm.getFileName()+" "+fm.getSize()+" -byte[]size_ simple send");
                    ctx.writeAndFlush(fm);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else {
            //если размер превышает FILE_PACK_SIZE
                Thread thread = new Thread(() -> {
                    ButtonBlocks buttonBlocksTrue = new ButtonBlocks(true);
                    ctx.writeAndFlush(buttonBlocksTrue);
                    try(FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[Constants.FILE_PACK_SIZE];
                        long packages = (long) Math.ceil((double) size / Constants.FILE_PACK_SIZE);
                        long partCount = packages - 1;
                        int readBytes;
                        while ((readBytes = fis.read(buffer)) != -1){
                            FileMessage fileMessage = FileMessage.builder()
                                    .multipart(true)
                                    .fileName(fileRequest.getFileName())
                                    .bytes(Arrays.copyOf(buffer, readBytes))
                                    .size(readBytes)
                                    .build();
                            ctx.writeAndFlush(fileMessage);
                            System.out.println("send pack: "+partCount+" "+fileRequest.getFileName());
                            partCount--;
                        }
                   } catch (IOException e) {
                        throw new RuntimeException(e);
                    }finally {
                        ButtonBlocks buttonBlocksFalse = new ButtonBlocks(false);
                        ctx.writeAndFlush(buttonBlocksFalse);
                    }
                });
                thread.setDaemon(true);
                thread.start();
                }
        }
    }

//end
}












