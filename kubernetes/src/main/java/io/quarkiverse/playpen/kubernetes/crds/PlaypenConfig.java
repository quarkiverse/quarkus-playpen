package io.quarkiverse.playpen.kubernetes.crds;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1")
@Group("io.quarkiverse.playpen")
public class PlaypenConfig extends CustomResource<PlaypenConfigSpec, PlaypenConfigStatus>
        implements Namespaced {
}