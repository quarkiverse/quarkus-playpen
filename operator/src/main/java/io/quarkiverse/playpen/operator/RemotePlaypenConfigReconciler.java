package io.quarkiverse.playpen.operator;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(namespaces = WATCH_ALL_NAMESPACES, name = "remoteplaypenconfig")
public class RemotePlaypenConfigReconciler implements Reconciler<RemotePlaypenConfig> {
    @Override
    public UpdateControl<RemotePlaypenConfig> reconcile(RemotePlaypenConfig resource, Context<RemotePlaypenConfig> context)
            throws Exception {
        return UpdateControl.patchStatus(resource);
    }
}
