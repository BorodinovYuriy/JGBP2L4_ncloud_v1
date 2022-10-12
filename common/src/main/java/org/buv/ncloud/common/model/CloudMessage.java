package org.buv.ncloud.common.model;

import java.io.Serializable;

public interface CloudMessage extends Serializable {
    MessageType getType();
}
