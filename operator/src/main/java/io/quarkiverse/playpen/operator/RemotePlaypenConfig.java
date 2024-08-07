package io.quarkiverse.playpen.operator;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1")
@Group("io.quarkiverse.playpen")
public class RemotePlaypenConfig extends CustomResource<RemotePlaypenConfigSpec, RemotePlaypenConfigStatus>
        implements Namespaced {
}