package org.buv.ncloud.server.serial;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.buv.ncloud.constant.Constants;
import org.buv.ncloud.model.*;
import org.buv.ncloud.server.AuthServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.file.StandardOpenOption.APPEND;

@Slf4j
public class FileHandler extends SimpleChannelInboundHandler<CloudMessage> {

    private final String serverDirectory = "server_files";
    private String serverUserLogin;//Логин нашего Юзера
    private Path userDir;
    private Path stop;
    private Path cutFile;
    final Lock lock = new ReentrantLock();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, CloudMessage cloudMessage) throws Exception {
        log.debug("Received: {}", cloudMessage.getType());

    //AUTHENTICATION
        if(cloudMessage instanceof Authentication authentication){
            //продумать logIsBusy!!!!!!!!!!!!!!
            String login = AuthServer.getUser(authentication.getLogin(),authentication.getPassword());
            if(login != null){
                serverUserLogin = login;
                log.info("User {} is Online.",serverUserLogin);
                userDir = Path.of(serverDirectory,"_" + login);
                stop = Path.of(serverDirectory);
                File userDirFileDir = userDir.toFile();
                if(!userDirFileDir.exists()){
                    Files.createDirectory(userDir);
                }
                ctx.writeAndFlush(new ListMessage(userDir));
            } else {
                ctx.writeAndFlush(new AuthError());
            }
        }
    //--------------------------------------------------------
    //FILE_MESSAGE (заливка)
        if (cloudMessage instanceof FileMessage fileMessage) {
            saveFileFromUser(fileMessage);
            ctx.writeAndFlush(new ListMessage(userDir));
        }
    //FILE_REQUEST (скачивание)
        if (cloudMessage instanceof FileRequest fileRequest) {
            fileRequestResponse(ctx, fileRequest);
        }
    //LIST_REQUEST (UPDATE_SERVER_VIEW)
        if (cloudMessage instanceof ListRequest listRequest) {
            String selected = listRequest.getSelectedItem();
            Path path = Paths.get(String.valueOf(userDir),selected).normalize();
            if(path.toFile().isDirectory()){
                if(!path.equals(stop)){
                    userDir = path;
                    ctx.writeAndFlush(new ListMessage(userDir));
                }
            }
        }
    //DELETE_REQUEST (директорию сделать!!!)
        if (cloudMessage instanceof DeleteRequest deleteRequest) {
            Path fileToBeDeletedPath = Paths.get(String.valueOf((userDir)),deleteRequest.getFileToDeleteName());
            try {
                if(fileToBeDeletedPath.toFile().isFile()){
                    Files.delete(fileToBeDeletedPath);
                    ctx.writeAndFlush(new ListMessage(userDir));
                }else if(fileToBeDeletedPath.toFile().isDirectory()){
                    //рекурсивное удаление папок и файлов
                    deleteFolder(fileToBeDeletedPath);
                    ctx.writeAndFlush(new ListMessage(userDir));
                }

            } catch (IOException e) {
                System.out.println("Попытка удаления не пустой директории!!");
            }
        }
    //MOVE_REQUEST
        if (cloudMessage instanceof MoveRequest moveRequest) {
            Path source = Paths.get(String.valueOf(userDir),moveRequest.getSource()).normalize();
            Path destination = Paths.get(String.valueOf(userDir),moveRequest.getDestination()).normalize();

            if(destination.toString().startsWith(stop.toString()+"/_"+serverUserLogin)){
                try{
                    Files.move(source,destination);
                }catch (IOException e){
                    System.out.println("Попытка переместить файл в не существующий каталог!");
                }
                ctx.writeAndFlush(new ListMessage(userDir));
            }else{
                System.out.println("Сохранение в недопустимом месте!");
                System.out.println(source);
                System.out.println(destination);
                System.out.println(stop.toString()+"/_"+serverUserLogin);
                System.out.println(userDir);
            }
        }
    //CREATE_DIRECTORY
        if (cloudMessage instanceof CreateDirOnServerRequest) {
            createDirectoryOnServer();
            ctx.writeAndFlush(new ListMessage(userDir));
        }
    //ADD_NEW_USER
        if (cloudMessage instanceof CreateUserRequest cur) {
            if(AuthServer.createNewUser(cur.getLogin(),cur.getPassword())){
                log.info("Add new user: {}",cur.getLogin());
                ctx.writeAndFlush(new AcceptUser(cur.getLogin()));
            }else {
                ctx.writeAndFlush(new UserExistError());
            }
        }
    //DELETE_USER
        if (cloudMessage instanceof DeleteUserRequest dur) {
            AuthServer.deleteUserFromBD(dur.getDeleteUser());
            Files.delete(userDir);//!
        }
    //CUT_REQUEST
        if (cloudMessage instanceof CutRequest cr){
            cutFile = Paths.get(String.valueOf(userDir),cr.getGetFilename());
        }
    //PASTE_REQUEST
        if (cloudMessage instanceof PasteRequest){
            Path source = cutFile;
            Path destination = userDir;
            if(!Files.isDirectory(source)){
                Files.move(source,destination);
            } else if (Files.isDirectory(source)) {
                FileUtils.moveToDirectory(source.toFile(), destination.toFile(),true);
                ctx.writeAndFlush(new ListMessage(userDir));
            }
        }
    }

    private void createDirectoryOnServer() {
        //потом объединить в общий
        try {
            File file = Paths.get(String.valueOf(userDir),"newDir").toFile();
            if(!file.exists()){
                Files.createDirectory(file.toPath());
            }else {
                for (int i = 0; i < 10000; i++){
                    File dir = Paths.get(String.valueOf(userDir),"newDir"+i).toFile();
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
                Files.write(userDir.resolve(fileMessage.getFileName()), fileMessage.getBytes());
            } catch (IOException e) {
                System.out.println("Upload (little file) error");
                e.printStackTrace();
            }
        }else {
            //тут страховку и поток организовать
            File file = Paths.get(String.valueOf(userDir),fileMessage.getFileName()).toFile();
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
        File file = userDir.resolve(fileRequest.getFileName()).toFile();
        if(file.exists()){
                long size = Files.size(userDir.resolve(fileRequest.getFileName()));
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
                    lock.lock();
                    /*ButtonBlocks buttonBlocksTrue = new ButtonBlocks(true);
                    ctx.writeAndFlush(buttonBlocksTrue);*/
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
                        e.printStackTrace();
                        log.debug("fileRequestResponse error");
                    }finally {
                        lock.unlock();
                        /*ButtonBlocks buttonBlocksFalse = new ButtonBlocks(false);
                        ctx.writeAndFlush(buttonBlocksFalse);*/
                    }
                });
                thread.setDaemon(true);
                thread.start();
                }
        }
    }
    private void deleteFolder(Path path) {
        try {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

//end
}












