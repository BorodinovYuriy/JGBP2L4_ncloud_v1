module org.buv.ncloud.client_simple {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.buv.ncloud.common;
    requires lombok;
    requires io.netty.codec;
    requires org.apache.commons.io;


    opens org.buv.ncloud.client_simple to javafx.fxml;
    exports org.buv.ncloud.client_simple;
}