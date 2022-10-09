package org.buv.ncloud.model;

import lombok.Getter;

@Getter
public class DeleteUserRequest implements CloudMessage{
    String deleteUser;

    public DeleteUserRequest(String deleteUser) {
        this.deleteUser = deleteUser;

    }

    @Override
    public MessageType getType() {
        return MessageType.DELETE_USER;
    }
}
