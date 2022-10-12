package org.buv.ncloud.common.model;

public class CreateDirOnServerRequest implements CloudMessage {

    @Override
    public MessageType getType() {
        return MessageType.CREATE_DIRECTORY;
    }
}
