package org.example.parser.wb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CookieFileLoaderTest {

    @TempDir
    private Path tempDir;

    @Test
    void treatsEachCookieHeaderLineAsSeparateCookieSet() throws Exception {
        Path cookieFile = tempDir.resolve("cookie.txt");
        Files.writeString(cookieFile, String.join(System.lineSeparator(),
                "Cookie: first=value",
                "Cookie: second=value",
                "Cookie: third=value"
        ));

        CookieRotator rotator = CookieFileLoader.load(cookieFile, 50, 20, 0.20);

        assertThat(rotator.size()).isEqualTo(3);
        assertThat(rotator.currentHeader()).isEqualTo("first=value");
    }
}
