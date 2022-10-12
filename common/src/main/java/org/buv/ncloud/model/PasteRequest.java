package org.buv.ncloud.model;

public class PasteRequest implements CloudMessage{
    @Override
    public MessageType getType() {
        return MessageType.PASTE_REQUEST;
    }
}
