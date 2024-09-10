package io.quarkiverse.playpen.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.playpen.client.LocalPlaypenConnectionConfig;

public class ConfigParseTest {

    @Test
    public void testHttpMinus() {
        String cli = "http://foo/prefix -who bill -query x=x,y=y -p /foo,/bar -header h=h -ip 192 -g";
        LocalPlaypenConnectionConfig config = LocalPlaypenConnectionConfig.fromCli(cli);
        Assertions.assertEquals("foo", config.host);
        Assertions.assertEquals(80, config.port);
        Assertions.assertEquals("/prefix", config.prefix);
        Assertions.assertEquals("bill", config.who);
        Assertions.assertNotNull(config.queries);
        Assertions.assertEquals(2, config.queries.size());
        Assertions.assertEquals("x=x", config.queries.get(0));
        Assertions.assertEquals("y=y", config.queries.get(1));
        Assertions.assertNotNull(config.paths);
        Assertions.assertEquals(2, config.paths.size());
        Assertions.assertEquals("/foo", config.paths.get(0));
        Assertions.assertEquals("/bar", config.paths.get(1));
        Assertions.assertNotNull(config.headers);
        Assertions.assertEquals(1, config.headers.size());
        Assertions.assertEquals("h=h", config.headers.get(0));
        Assertions.assertNotNull(config.clientIp);
        Assertions.assertEquals("192", config.clientIp);
        Assertions.assertTrue(config.isGlobal);

    }

    @Test
    public void testHttpMinusMinus() {
        String cli = "http://foo/prefix --who=bill --query=x=x,y=y --path=/foo,/bar --header=h=h --clientIp=192 --global";
        LocalPlaypenConnectionConfig config = LocalPlaypenConnectionConfig.fromCli(cli);
        Assertions.assertEquals("foo", config.host);
        Assertions.assertEquals(80, config.port);
        Assertions.assertEquals("/prefix", config.prefix);
        Assertions.assertEquals("bill", config.who);
        Assertions.assertNotNull(config.queries);
        Assertions.assertEquals(2, config.queries.size());
        Assertions.assertEquals("x=x", config.queries.get(0));
        Assertions.assertEquals("y=y", config.queries.get(1));
        Assertions.assertNotNull(config.paths);
        Assertions.assertEquals(2, config.paths.size());
        Assertions.assertEquals("/foo", config.paths.get(0));
        Assertions.assertEquals("/bar", config.paths.get(1));
        Assertions.assertNotNull(config.headers);
        Assertions.assertEquals(1, config.headers.size());
        Assertions.assertEquals("h=h", config.headers.get(0));
        Assertions.assertNotNull(config.clientIp);
        Assertions.assertEquals("192", config.clientIp);
        Assertions.assertTrue(config.isGlobal);

    }
}
