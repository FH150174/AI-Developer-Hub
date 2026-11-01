package com.fhaicl.aidevhub.ui;

import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppIdentityTest {
    @Test void applicationNameMatchesPackagingMetadata() throws Exception {
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Path.of("pom.xml").toFile());
        assertEquals(AppIdentity.NAME, document.getElementsByTagName("name").item(0).getTextContent());
    }

    @Test void reportsPackagedVersionAndDevelopmentFallback() {
        String previous = System.getProperty("jpackage.app-version");
        try {
            System.clearProperty("jpackage.app-version");
            assertEquals("开发版", AppIdentity.version());
            System.setProperty("jpackage.app-version", "1.0.0");
            assertEquals("v1.0", AppIdentity.version());
            System.setProperty("jpackage.app-version", "1.0.1");
            assertEquals("v1.0.1", AppIdentity.version());
        } finally {
            if (previous == null) System.clearProperty("jpackage.app-version");
            else System.setProperty("jpackage.app-version", previous);
        }
    }

    @Test void everyWindowIconIsPackagedAndDecodesAtItsDeclaredSize() throws Exception {
        for (int size : new int[]{16, 32, 48, 64, 128, 256}) {
            try (var input = AppIdentity.class.getResourceAsStream("icons/app-" + size + ".png")) {
                assertNotNull(input);
                var image = ImageIO.read(input);
                assertNotNull(image);
                assertEquals(size, image.getWidth());
                assertEquals(size, image.getHeight());
            }
        }
    }
}
