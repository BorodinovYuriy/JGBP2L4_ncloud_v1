package org.buv.ncloud.model;

public class UserExistError implements CloudMessage{
    @Override
    public MessageType getType() {
        return MessageType.USER_EXIST_ERROR;
    }
}
