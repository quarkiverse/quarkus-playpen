package io.quarkiverse.playpen.test;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "openshift", matches = "true")
public class OpenshiftCliPortForwardTest extends K8sCliPortForwardTest {

}
