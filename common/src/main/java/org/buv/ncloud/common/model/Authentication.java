package org.buv.ncloud.common.model;

import lombok.Getter;

@Getter
public class Authentication implements CloudMessage{
    private final String login;
    private final String password;

    public Authentication(String login, String password) {
        this.login = login;
        this.password = password;
    }

    @Override
    public MessageType getType() {
        return MessageType.AUTHENTICATION;
    }
}
