package org.buv.ncloud.common.model;

import lombok.Getter;

@Getter
public class ListRequest implements CloudMessage{

    private final String selectedItem;

    public ListRequest(String selectedItem) {
        this.selectedItem = selectedItem;
    }

    @Override
    public MessageType getType() {
        return MessageType.UPDATE_SERVER_VIEW;
    }
}
