package org.buv.ncloud.model;

import lombok.Getter;

@Getter
public class CutRequest implements CloudMessage{
    String getFilename;

    public CutRequest(String getFilename) {
        this.getFilename = getFilename;
    }

    @Override
    public MessageType getType() {
        return MessageType.CUT_REQUEST;
    }
}
