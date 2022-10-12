package org.buv.ncloud.common.model;

public class UserExistError implements CloudMessage{
    @Override
    public MessageType getType() {
        return MessageType.USER_EXIST_ERROR;
    }
}
