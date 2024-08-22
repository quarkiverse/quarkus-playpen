package io.quarkiverse.playpen.test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.playpen.client.RemotePlaypenClient;
import io.quarkiverse.playpen.server.QuarkusPlaypenServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(RemotePlaypenManagerTestCase.ConfigOverrides.class)
public class RemotePlaypenManagerTestCase {
    public static class ConfigOverrides implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "service.host", "localhost",
                    "service.name", "my-service",
                    "service.port", "9091",
                    "client.api.port", "8082",
                    "quarkus.log.category.\"io.quarkiverse.playpen\".level", "DEBUG");
        }
    }

    @Inject
    QuarkusPlaypenServer server;

    @Test
    public void testUploadDownload() throws Exception {

        Assertions.assertNotNull(server);
        RemotePlaypenClient client = new RemotePlaypenClient("http://localhost:8082/remote/bill", "", "");

        File file = File.createTempFile("project", ".txt");
        FileOutputStream fos = new FileOutputStream(file);
        fos.write("hello".getBytes(StandardCharsets.UTF_8));
        fos.close();

        server.getProxyServer().getConfig().basePlaypenDirectory = file.toPath().getParent().resolve("playpens").toString();

        Assertions.assertTrue(client.create(file.toPath(), true));
        File download = File.createTempFile("download", ".txt");
        Assertions.assertTrue(client.download(download.toPath()));
        Assertions.assertTrue(download.exists());
        Assertions.assertEquals("hello", FileUtils.readFileToString(download, "UTF-8"));

    }
}
