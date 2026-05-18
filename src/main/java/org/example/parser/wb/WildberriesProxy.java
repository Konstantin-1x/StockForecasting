package org.example.parser.wb;

import java.net.InetSocketAddress;
import java.net.Proxy;

record WildberriesProxy(String host, int port, String username, String password, boolean secure) {

    WildberriesProxy(String host, int port, String username, String password) {
        this(host, port, username, password, false);
    }

    Proxy toJavaProxy() {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
    }

    boolean hasCredentials() {
        return username != null && !username.isBlank();
    }

    String safeLabel() {
        return host + ":" + port;
    }
}
