package org.buv.ncloud.model;

public class AcceptUser implements CloudMessage{
    String acceptedLogin;

    public AcceptUser(String login) {
        this.acceptedLogin = login;
    }

    @Override
    public MessageType getType() {
        return MessageType.ACCEPT_USER;
    }
}
