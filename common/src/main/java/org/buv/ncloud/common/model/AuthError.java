package org.buv.ncloud.common.model;

public class AuthError implements CloudMessage{
    @Override
    public MessageType getType() {
        return MessageType.AUTH_ERROR;
    }
}
