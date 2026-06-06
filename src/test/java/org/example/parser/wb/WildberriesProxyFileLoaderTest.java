package org.example.parser.wb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildberriesProxyFileLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void appliesDefaultCredentialsToHostPortProxy() throws Exception {
        Path proxyFile = tempDir.resolve("proxies.txt");
        Files.writeString(proxyFile, "127.0.0.1:8085");

        WildberriesProxyPool pool = WildberriesProxyFileLoader.load(
                proxyFile,
                Duration.ofMinutes(5),
                false,
                Duration.ofSeconds(1),
                1,
                "proxy-user",
                "proxy-password"
        );

        WildberriesProxy proxy = pool.randomProxy();

        assertEquals("127.0.0.1", proxy.host());
        assertEquals(8085, proxy.port());
        assertEquals("proxy-user", proxy.username());
        assertEquals("proxy-password", proxy.password());
    }

    @Test
    void keepsCredentialsFromProxyLineWhenPresent() throws Exception {
        Path proxyFile = tempDir.resolve("proxies.txt");
        Files.writeString(proxyFile, "127.0.0.1:8085:line-user:line-password");

        WildberriesProxyPool pool = WildberriesProxyFileLoader.load(
                proxyFile,
                Duration.ofMinutes(5),
                false,
                Duration.ofSeconds(1),
                1,
                "default-user",
                "default-password"
        );

        WildberriesProxy proxy = pool.randomProxy();

        assertTrue(proxy.hasCredentials());
        assertEquals("line-user", proxy.username());
        assertEquals("line-password", proxy.password());
    }

    @Test
    void marksProxyAsSecureWhenDefaultSecureIsEnabled() throws Exception {
        Path proxyFile = tempDir.resolve("proxies.txt");
        Files.writeString(proxyFile, "proxy.example.com:8444:line-user:line-password");

        WildberriesProxyPool pool = WildberriesProxyFileLoader.load(
                proxyFile,
                Duration.ofMinutes(5),
                false,
                Duration.ofSeconds(1),
                1,
                null,
                null,
                true
        );

        WildberriesProxy proxy = pool.randomProxy();

        assertTrue(proxy.secure());
    }

    @Test
    void keepsProxyNonSecureWhenDefaultSecureIsDisabled() throws Exception {
        Path proxyFile = tempDir.resolve("proxies.txt");
        Files.writeString(proxyFile, "proxy.example.com:8085:line-user:line-password");

        WildberriesProxyPool pool = WildberriesProxyFileLoader.load(
                proxyFile,
                Duration.ofMinutes(5),
                false,
                Duration.ofSeconds(1),
                1,
                null,
                null,
                false
        );

        WildberriesProxy proxy = pool.randomProxy();

        assertFalse(proxy.secure());
    }
}
