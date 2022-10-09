package org.buv.ncloud.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;

public class AuthServer {
    private static Connection connection;
    private static Statement statement;

    public static void connect() {
        Path pathToDB = Paths.get("server_files","ncloud_users.db");
        if(pathToDB.toFile().exists()){
            try {
                //находим класс драйвера
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:"+ pathToDB);
                statement = connection.createStatement();
            } catch (ClassNotFoundException | SQLException e) {
                e.printStackTrace();
                System.out.println("connect() sqlite is failed!!!!!!!!!!!!!!!!!");
            }
        }else{
            try {
                Files.createFile(pathToDB);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:"+ pathToDB);
                statement = connection.createStatement();
                PreparedStatement createDB_PS = connection.prepareStatement
                        ("CREATE TABLE IF NOT EXISTS \"users\" (\n" +
                        "\t\"id\"\tINTEGER NOT NULL UNIQUE,\n" +
                        "\t\"login\"\tTEXT NOT NULL UNIQUE,\n" +
                        "\t\"password\"\tTEXT NOT NULL,\n" +
                        "\tPRIMARY KEY(\"id\" AUTOINCREMENT)\n" +
                        ");");
                createDB_PS.execute();
                //заполнение (ТЕСТ)
                /*PreparedStatement addUsersTest = connection.prepareStatement
                        ("INSERT INTO \"users\" VALUES (1,'1','1');");
                addUsersTest.execute();
                System.out.println("Создана новая база, тестовые log/pass:  1/1  !!!");*/
            } catch (ClassNotFoundException | SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }


    public static void disconnect() {
        //закрыли всё
        try {
            if (statement != null) {
                statement.close();
            }
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("disconnect AuthServer exception");
        }
    }

    public static String getUser(String log, String pass) {
        try {
            PreparedStatement preparedStatement = connection.prepareStatement("select login from users where login = ? and password = ?");
            preparedStatement.setString(1, log);
            preparedStatement.setString(2, pass);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
        } catch (SQLException e) {
            System.out.println("getNickByLogPassSQL проблема");
            e.printStackTrace();
        }
        return null;
    }

    public static boolean createNewUser (String log, String pass) {
        try {
                PreparedStatement preparedStatement = connection.prepareStatement("insert into users (login, password) values (?,?)");
                preparedStatement.setString(1, log);
                preparedStatement.setString(2, pass);
                preparedStatement.execute();
            System.out.println("createNewUser() "+log);
            return true;
        } catch (SQLException e) {
            System.out.println("Логин занят или сервер не доступен _SQL exception");
            return false;
        }
    }
    public static void deleteUserFromBD(String login) {
        try {
            PreparedStatement preparedStatement = connection.prepareStatement("DELETE FROM users WHERE login = ?");
            preparedStatement.setString(1, login);
            preparedStatement.execute();
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("SQL deleteUserFromBD() error!!!");
        }
    }

    public static boolean isUserFree(String log) {
        boolean answer = false;
            try {
                PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM users WHERE login = ?");
                preparedStatement.setString(1, log);
                ResultSet resultSet = preparedStatement.executeQuery();
                if (!resultSet.next()) {
                    answer = true;
                }
            } catch (SQLException e) {
                System.out.println("isUserFree проблема");
                e.printStackTrace();
            }
        return answer;
    }
}