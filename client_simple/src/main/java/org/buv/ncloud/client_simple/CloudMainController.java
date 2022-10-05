package org.buv.ncloud.client_simple;


import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import org.buv.ncloud.constant.Constants;
import org.buv.ncloud.model.*;
import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import org.buv.ncloud.utils.DaemonThreadFactory;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import static java.nio.file.StandardOpenOption.APPEND;


public class CloudMainController implements Initializable {
    public ListView<String> clientView;
    public ListView<String> serverView;
    public Button writeToServerButton;
    public Button downloadFromServerButton;
    public AnchorPane allAnchorPane;
    public TextField renameTextField;
    public Button renameFileOnClientButton;
    public TextField oldNameTextField;
    public Button renameFileOnServerButton;
    private String currentDirectory;
    //--------------------

    private Network<ObjectDecoderInputStream, ObjectEncoderOutputStream> network;
    
    private Socket socket;

    private boolean needReadMessages = true;

    private DaemonThreadFactory factory;


    private List<String> getFilesListOnClient(String directory) {
        // file.txt 125 b
        // dir [DIR]
        File dir = new File(directory);
        if (dir.isDirectory()) {
            String[] list = dir.list();
            if (list != null) {
                List<String> files = new ArrayList<>(Arrays.asList(list));
                files.add(0, "..");
                return files;
            }
        }
        return List.of();
    }

    private void initNetwork() {
        try {
            socket = new Socket("localhost", 8189);
            network = new Network<>(
                    new ObjectDecoderInputStream(socket.getInputStream()),
                    new ObjectEncoderOutputStream(socket.getOutputStream())
            );
            factory.getThread(this::readMessages, "cloud-client-read-thread")
                    .start();

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "В соединении отказано,\nлибо сервер отключен!",
                    ButtonType.OK);
            alert.showAndWait();
            System.exit(0);
        }
    }

    private void fillView(ListView<String> view, List<String> data) {
        view.getItems().clear();
        view.getItems().addAll(data);
    }

    private void doubleClickOnServerView(String selected) {
        try {
            network.getOutputStream().writeObject(new ListRequest(selected));
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private void setCurrentDirectory(String directory) {
        currentDirectory = directory;
        fillView(clientView, getFilesListOnClient(currentDirectory));
    }

    public void sendCommand_downloadFileFromServer(ActionEvent actionEvent) throws IOException {
        String fileName = serverView.getSelectionModel().getSelectedItem();
        network.getOutputStream().writeObject(new FileRequest(fileName));
    }

    //---------------------------------------------------------------
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        needReadMessages = true;
        //DaemonThreadFactory utils common
        factory = new DaemonThreadFactory();//используется в initNetwork
        initNetwork();
        setCurrentDirectory(System.getProperty("user.home"));
        fillView(clientView, getFilesListOnClient(currentDirectory));
        clientView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = clientView.getSelectionModel().getSelectedItem();
                File selectedFile = Paths.get(currentDirectory,selected).toFile();
                /*File selectedFile = new File(currentDirectory + "/" + selected);*/
                if (selectedFile.isDirectory()) {
                    setCurrentDirectory(String.valueOf(Paths.get(currentDirectory,selected)));
                    /*setCurrentDirectory(currentDirectory + "/" + selected);*/
                }
            }
        });
        //Ивент мыши на serverView!!!
        serverView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = serverView.getSelectionModel().getSelectedItem();
                doubleClickOnServerView(selected);
            }
        });
        //---------ContextMenu----------
        ContextMenu contextMenu = new ContextMenu();
        MenuItem createDirectory = new MenuItem("create dir");
        createDirectory.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if(clientView.isFocused()){
                    createDirectoryOnClient();
                    Platform.runLater(() -> fillView(clientView, getFilesListOnClient(currentDirectory)));
                }
                if(serverView.isFocused()){
                    createDirectoryOnServer();
                }
            }
        });


        MenuItem delete = new MenuItem("delete");
        delete.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if(clientView.isFocused()){
                    String name = clientView.getSelectionModel().getSelectedItem();
                    if(name != null){
                        String filename = String.valueOf(Paths.get(currentDirectory,name));
                        /*String filename = currentDirectory+"/"+ name;*/
                        try {
                            Files.delete(Path.of(filename));
                            Platform.runLater(() -> fillView(clientView, getFilesListOnClient(currentDirectory)));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                if(serverView.isFocused()){
                    String deleteFileOnServerName = serverView.getSelectionModel().getSelectedItem();
                    if(deleteFileOnServerName != null){
                        try {
                            network.getOutputStream().writeObject(new DeleteRequest(deleteFileOnServerName));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        });
        MenuItem rename = new MenuItem("rename");
        rename.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if(clientView.isFocused()){
                    String name = clientView.getSelectionModel().getSelectedItem();
                    if(name != null){
                        String filename = String.valueOf(Paths.get(currentDirectory,name));
                                /*String filename = currentDirectory+"/"+ name;*/
                        renameTextField.setVisible(true);
                        renameFileOnClientButton.setVisible(true);
                        oldNameTextField.setVisible(true);

                        oldNameTextField.setText(name);
                        renameTextField.setText(name);
                    }
                }
                if(serverView.isFocused()){
                    String moveFileOnServerName = serverView.getSelectionModel().getSelectedItem();
                    if(moveFileOnServerName != null){
                        renameTextField.setVisible(true);
                        oldNameTextField.setVisible(true);
                        renameFileOnServerButton.setVisible(true);

                        oldNameTextField.setText(moveFileOnServerName);
                        renameTextField.setText(moveFileOnServerName);

                    }
                }
            }
        });

        contextMenu.getItems().addAll(createDirectory,delete,rename);
        clientView.setContextMenu(contextMenu);
        serverView.setContextMenu(contextMenu);
        //-------------------------------------------------------
    }

    private void createDirectoryOnServer() {
        try {
            network.getOutputStream().writeObject(new CreateDirOnServerRequest());
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Не удалось создать папку",
                    ButtonType.OK);
            alert.showAndWait();
        }

    }

    private void createDirectoryOnClient() {
        //потом объединить в общий
        try {
            File file = Paths.get(currentDirectory,"newDir").toFile();
            if(!file.exists()){
                Files.createDirectory(file.toPath());
            }else {
                for (int i = 0; i < 10000; i++){
                    File dir = Paths.get(currentDirectory,"newDir"+i).toFile();
                    if (!dir.exists()){
                        Files.createDirectory(dir.toPath());
                        break;
                    }
                }
            }
        }catch (IOException e){
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Не удалось создать папку",
                    ButtonType.OK);
            alert.showAndWait();
        }
    }

    public void renameFileOnServer(ActionEvent actionEvent){
        if(!renameTextField.getText().isBlank()){
            String source = oldNameTextField.getText();
            String destination = renameTextField.getText().trim();
            try {
                network.getOutputStream().writeObject(new MoveRequest(source,destination));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        renameTextField.setVisible(false);
        oldNameTextField.setVisible(false);
        renameFileOnServerButton.setVisible(false);

    }

    public void renameFileOnClient(ActionEvent actionEvent) {
            if(!renameTextField.getText().isBlank()){
                try {
                    Files.move((Paths.get(currentDirectory,oldNameTextField.getText()))
                            ,(Paths.get(currentDirectory,renameTextField.getText())));
                    /*Files.move(Path.of(currentDirectory + "/" + oldNameTextField.getText())
                            , Path.of(currentDirectory + "/" + renameTextField.getText()));*/
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Platform.runLater(() -> fillView(clientView, getFilesListOnClient(currentDirectory)));
            }
            renameTextField.setVisible(false);
            renameFileOnClientButton.setVisible(false);
            oldNameTextField.setVisible(false);
    }

    public void writeFileToServer(ActionEvent actionEvent) throws IOException {
        String fileName = clientView.getSelectionModel().getSelectedItem();
        long size = Files.size((Path.of(currentDirectory,fileName)));
        File file = Paths.get(currentDirectory,fileName).toFile();
        /*File file = new File(currentDirectory + "/" + fileName);*/
        if(file.exists() && file.isFile()){
            //если размер не превышает FILE_PACK_SIZE
            if (size <= Constants.FILE_PACK_SIZE){
                try (FileInputStream fis = new FileInputStream(file)){
                    FileMessage fm = FileMessage.builder()
                            .multipart(false)
                            .fileName(fileName)
                            .bytes(fis.readAllBytes())
                            .size(size)
                            .build();
                    network.getOutputStream().writeObject(fm);
                }
            //если размер превышает FILE_PACK_SIZE
            }else{
                Thread thread = new Thread(() -> {
                    //страховка
                    writeToServerButton.setDisable(true);
                    downloadFromServerButton.setDisable(true);
                    try(FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[Constants.FILE_PACK_SIZE];
                        long packages = (long) Math.ceil((double) size / Constants.FILE_PACK_SIZE);
                        long partCount = packages - 1;
                        int readBytes;
                        while ((readBytes = fis.read(buffer)) != -1){
                            FileMessage fileMessage = FileMessage.builder()
                                    .multipart(true)
                                    .fileName(fileName)
                                    .bytes(Arrays.copyOf(buffer, readBytes))
                                    .size(readBytes)
                                    .build();
                            network.getOutputStream().writeObject(fileMessage);
                            System.out.println("send pack: "+partCount+" "+fileName);
                            partCount--;
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }finally {
                        //страховка
                        writeToServerButton.setDisable(false);
                        downloadFromServerButton.setDisable(false);
                    }
                });
                thread.setDaemon(true);
                thread.start();

            }
        }
    }
    //Прием сообщений с сервера................................
    private void readMessages() {
        try {
            while (needReadMessages) {
                CloudMessage message = (CloudMessage) network.getInputStream().readObject();
                if (message instanceof FileMessage fileMessage) {
                    //чтение fileMessage
                    readFileMessage(fileMessage);
                    //Обновление списка файлов
                    Platform.runLater(() -> fillView(clientView, getFilesListOnClient(currentDirectory)));
                } else if (message instanceof ListMessage listMessage) {
                    //показывает файлы сервера
                    Platform.runLater(() -> fillView(serverView, listMessage.getFiles()));
                } else if (message instanceof ButtonBlocks buttonBlocks){
                    if(buttonBlocks.isDoBlockTheButton()){
                        writeToServerButton.setDisable(true);
                        downloadFromServerButton.setDisable(true);
                    }else {
                        writeToServerButton.setDisable(false);
                        downloadFromServerButton.setDisable(false);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Server off");
            e.printStackTrace();
        }
    }

    private void readFileMessage(FileMessage fileMessage) throws IOException {
        //переписать в общий метод
        if(!fileMessage.isMultipart()){
            try {
                Files.write(Path.of(currentDirectory).resolve(fileMessage.getFileName()), fileMessage.getBytes());
            } catch (IOException e) {
                System.out.println("Download from server (little file) error");
                e.printStackTrace();
            }
        }else {
            //поток организовать
            File file = Paths.get(currentDirectory,fileMessage.getFileName()).toFile();
            /*File file = new File(currentDirectory+"/"+fileMessage.getFileName());*/
            if(!file.exists()){
                file.createNewFile();
            }
                    try {
                        Files.write(file.toPath(), fileMessage.getBytes(), APPEND);
                    } catch (IOException e) {
                        System.out.println("Download from server (big file) error");
                        e.printStackTrace();
                    }
            }
        }
}




