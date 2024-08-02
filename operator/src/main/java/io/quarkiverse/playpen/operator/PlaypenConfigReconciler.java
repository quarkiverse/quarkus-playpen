package io.quarkiverse.playpen.operator;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(namespaces = WATCH_ALL_NAMESPACES, name = "playpenconfig")
public class PlaypenConfigReconciler implements Reconciler<PlaypenConfig> {
    @Override
    public UpdateControl<PlaypenConfig> reconcile(PlaypenConfig resource, Context<PlaypenConfig> context)
            throws Exception {
        return UpdateControl.patchStatus(resource);
    }
}
