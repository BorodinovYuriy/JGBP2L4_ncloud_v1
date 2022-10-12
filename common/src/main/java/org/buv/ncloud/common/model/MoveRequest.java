package org.buv.ncloud.common.model;

import lombok.Getter;

@Getter
public class MoveRequest implements CloudMessage {

    String source;
    String destination;
    public MoveRequest(String source, String destination) {
        this.source = source;
        this.destination = destination;

    }

    @Override
    public MessageType getType() {
        return MessageType.MOVE_REQUEST;
    }
}
