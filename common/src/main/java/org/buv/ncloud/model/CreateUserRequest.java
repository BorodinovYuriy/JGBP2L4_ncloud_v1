package org.buv.ncloud.model;

import lombok.Getter;

@Getter
public class CreateUserRequest implements CloudMessage{
    private String login;
    private String password;

    public CreateUserRequest(String login, String password) {
        this.login = login;
        this.password = password;
    }

    @Override
    public MessageType getType() {
       return MessageType.ADD_NEW_USER;
    }
}
