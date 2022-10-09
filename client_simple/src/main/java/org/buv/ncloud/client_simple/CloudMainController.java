package org.buv.ncloud.client_simple;

import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.buv.ncloud.constant.Constants;
import org.buv.ncloud.model.*;
import org.buv.ncloud.utils.DaemonThreadFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.file.StandardOpenOption.APPEND;
@Slf4j
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
    public MenuItem deleteAccount;
    private String currentDirectory;
    //--------------------
    private String login;
    private String cuttingItem;
    private String cuttingItemFilename;

    private Network<ObjectDecoderInputStream, ObjectEncoderOutputStream> network;
    
    private Socket socket;

    private boolean needReadMessages = true;

    private DaemonThreadFactory factory;

    final Lock lock = new ReentrantLock();

    Label infoLabel = new Label("Введите логин и пароль!");//для окна регистрации


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
            openRegistrationWindow();

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
        //Ивенты мыши на serverView!!!
        clientView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = clientView.getSelectionModel().getSelectedItem();
                File selectedFile = Paths.get(currentDirectory,selected).toFile();
                if (selectedFile.isDirectory()) {
                    setCurrentDirectory(String.valueOf(Paths.get(currentDirectory,selected)));
                }
            }
        });
        serverView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = serverView.getSelectionModel().getSelectedItem();
                doubleClickOnServerView(selected);
            }
        });

        //---------ContextMenu----------
        ContextMenu contextMenu = new ContextMenu();

        MenuItem createDirectory = new MenuItem("create dir");
            createDirectory.setOnAction(event -> {
            if(clientView.isFocused()){
                createDirectoryOnClient();
                Platform.runLater(() -> fillView(clientView, getFilesListOnClient(currentDirectory)));
            }
            if(serverView.isFocused()){
                createDirectoryOnServer();
            }
        });
        MenuItem delete = new MenuItem("delete");
            delete.setOnAction(event -> {
            if(clientView.isFocused()){
                String name = clientView.getSelectionModel().getSelectedItem();
                Path filePath = Path.of(String.valueOf(Paths.get(currentDirectory, name))).normalize();
                if(name != null){
                    try{
                        if (!Files.isDirectory(filePath)){
                            Files.delete(filePath);
                        }else if (Files.isDirectory(filePath)){
                            Path pathNew = filePath.getParent();
                                FileUtils.deleteDirectory(filePath.toFile());
                                currentDirectory = pathNew.toString();
                        }
                    }catch (IOException e){
                        e.printStackTrace();
                    }finally {
                        Platform.runLater(() -> fillView(clientView, getFilesListOnClient(currentDirectory)));
                    }
                }
            }
            if(serverView.isFocused()){
                String deleteFileOnServerName = serverView.getSelectionModel().getSelectedItem();
                if(deleteFileOnServerName != null){
                    try {
                        network.getOutputStream().writeObject(new DeleteRequest(deleteFileOnServerName));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        MenuItem rename = new MenuItem("rename/move");
            rename.setOnAction(event -> {
            if(clientView.isFocused()){
                String name = clientView.getSelectionModel().getSelectedItem();
                if(name != null){
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
        });
        MenuItem cut = new MenuItem("cut");
            cut.setOnAction(event -> {
                if(clientView.isFocused()){
                    cuttingItem = (Paths.get(currentDirectory,
                            clientView.getSelectionModel().getSelectedItem())).toString();
                    cuttingItemFilename = clientView.getSelectionModel().getSelectedItem();
                }
                if (serverView.isFocused()){
                    try {
                        network.getOutputStream().writeObject(new CutRequest(serverView.getSelectionModel().getSelectedItem()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
        });
        MenuItem paste = new MenuItem("paste");
            paste.setOnAction(event -> {
                if(clientView.isFocused()){
                    if(cuttingItem != null){
                        Path destination = Paths.get(currentDirectory,cuttingItemFilename);
                        if(!Files.isDirectory(Path.of(cuttingItem))){
                            try {
                                Files.move( Paths.get(cuttingItem),destination);
                            }catch (IOException e){
                                e.printStackTrace();
                            } finally {
                                cuttingItem = null;
                                cuttingItemFilename = null;
                                Platform.runLater(() -> fillView(clientView, getFilesListOnClient(currentDirectory)));
                            }
                        } else if (Files.isDirectory(Path.of(cuttingItem))) {
                            try {
                                File srcDir = new File(cuttingItem);
                                File destDir = new File(currentDirectory);
                                FileUtils.copyDirectory(srcDir, destDir);
                                FileUtils.deleteDirectory(srcDir);
                            } catch (IOException  exception) {
                                exception.printStackTrace();
                            }finally {
                                cuttingItem = null;
                                cuttingItemFilename = null;
                                Platform.runLater(() -> fillView(clientView, getFilesListOnClient(currentDirectory)));
                            }
                        }

                    }
                }
                if (serverView.isFocused()){
                    try {
                        network.getOutputStream().writeObject(new PasteRequest());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

        });
        //Начальное добавление элементов
        contextMenu.getItems().addAll(createDirectory,cut,paste,rename,delete);
        clientView.setContextMenu(contextMenu);
        serverView.setContextMenu(contextMenu);
        //-------------------------------------------------------
    }

    private void openRegistrationWindow() {
        Stage window = new Stage();
        window.initModality(Modality.APPLICATION_MODAL);
        window.setOnCloseRequest(e -> System.exit(0));
        Pane pane = new Pane();

        TextField login = new TextField();
        login.setMaxWidth(200);
        login.setPromptText("Login");
        login.setFocusTraversable(false);

        TextField password = new TextField();
        password.setMaxWidth(200);
        password.setPromptText("Password");
        password.setFocusTraversable(false);

        Button confirmBtn = new Button("Confirm");
        confirmBtn.setMinSize(200,30);
        confirmBtn.setFocusTraversable(false);
        confirmBtn.setBackground(Background.fill(Color.CADETBLUE));

        Button addNewUserBtn = new Button("Add new User");
        addNewUserBtn.setMinSize(200,30);
        addNewUserBtn.setFocusTraversable(false);

        confirmBtn.setOnAction(event -> {

            if(login.getText().isBlank() | password.getText().isBlank()){
                infoLabel.setText("Поля не заполнены!");
            }else{
                Authentication auth = new Authentication(login.getText(),password.getText());
                try {
                    network.getOutputStream().writeObject(auth);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                this.login = login.getText();
                window.close();
            }
        });
        addNewUserBtn.setOnAction(event -> {
            if(login.getText().isBlank() | password.getText().isBlank()){
                infoLabel.setText("Поля не заполнены!");
            }else{
                CreateUserRequest cur = new CreateUserRequest(login.getText(),password.getText());
                try {
                    network.getOutputStream().writeObject(cur);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                window.close();
            }
        });

        VBox dataBox = new VBox(infoLabel
                                    ,login
                                    ,password
                                    ,confirmBtn
                                    ,addNewUserBtn);
        dataBox.setPadding(new Insets(5,5,5,5));
        pane.getChildren().add(dataBox);
        Scene scene = new Scene(pane,212,150);

        window.setScene(scene);
        window.setTitle("Регистрация");
        window.setResizable(false);
        window.showAndWait();

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
            }else{
                //если размер превышает FILE_PACK_SIZE
                Thread thread = new Thread(() -> {
                    lock.lock();
                    /*writeToServerButton.setDisable(true);
                    downloadFromServerButton.setDisable(true);*/
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
                        e.printStackTrace();
                        Alert alert = new Alert(Alert.AlertType.ERROR,
                                "Ошибка отправки данных на сервер!",
                                ButtonType.OK);
                        alert.showAndWait();
                        System.exit(0);
                    }finally {
                        //страховка
                        /*writeToServerButton.setDisable(false);
                        downloadFromServerButton.setDisable(false);*/
                        lock.unlock();
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
            //Приёмка файлов
                if (message instanceof FileMessage fileMessage) {
                    //чтение fileMessage
                    readFileMessage(fileMessage);
                    //Обновление списка файлов
                    Platform.runLater(() -> fillView(clientView, getFilesListOnClient(currentDirectory)));
            //Список файлов
                } else if (message instanceof ListMessage listMessage) {
                    //показывает файлы сервера
                    Platform.runLater(() -> fillView(serverView, listMessage.getFiles()));
            //Ошибка при регистрации
                } else if(message instanceof AuthError){
                    Platform.runLater(this::openRegistrationWindow);
                    infoLabel.setText("Логин занят или не существует!");
            //пользователь уже есть в базе
                } else if (message instanceof UserExistError) {
                    Platform.runLater(this::openRegistrationWindow);
                    infoLabel.setText("Логин занят!");
            //подтверждение создания пользователя
                } else if (message instanceof AcceptUser au) {
                    Platform.runLater(this::openRegistrationWindow);

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

    public void deleteThisAccount(ActionEvent actionEvent) {
        try {
            network.getOutputStream().writeObject(new DeleteUserRequest(login));
            System.out.println(login +"to delete!");
            openRegistrationWindow();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}




