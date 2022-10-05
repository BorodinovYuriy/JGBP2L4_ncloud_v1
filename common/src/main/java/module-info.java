module org.buv.ncloud.common {
    requires lombok;
    requires io.netty.transport;

    exports org.buv.ncloud.model;
    exports org.buv.ncloud.utils;
    exports org.buv.ncloud.constant;
}