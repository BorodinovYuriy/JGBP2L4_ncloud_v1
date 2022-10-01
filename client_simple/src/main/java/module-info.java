module org.buv.ncloud.client_simple {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.buv.ncloud.common;
    requires io.netty.codec;


    opens org.buv.ncloud.client_simple to javafx.fxml;
    exports org.buv.ncloud.client_simple;
}