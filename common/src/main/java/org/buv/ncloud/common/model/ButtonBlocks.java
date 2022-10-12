package org.buv.ncloud.common.model;

import lombok.Getter;

@Getter
public class ButtonBlocks implements CloudMessage{

    private final boolean doBlockTheButton;

    public ButtonBlocks(boolean doBlockTheButton) {
        this.doBlockTheButton = doBlockTheButton;
    }

    @Override
    public MessageType getType() {
        return MessageType.BLOCK_BUTTON;
    }
}
