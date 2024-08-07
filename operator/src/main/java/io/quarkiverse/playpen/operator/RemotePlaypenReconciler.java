package io.quarkiverse.playpen.operator;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import jakarta.inject.Inject;

import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.APIGroup;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceFluent;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressFluent;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;

@ControllerConfiguration(namespaces = WATCH_ALL_NAMESPACES, name = "remoteplaypen")
@CSVMetadata(displayName = "Remote Playpen operator", description = "Setup of Remote Playpen for a specific service")
public class RemotePlaypenReconciler implements Reconciler<RemotePlaypen>, Cleaner<RemotePlaypen> {
    protected static final Logger log = Logger.getLogger(RemotePlaypenReconciler.class);

    @Inject
    OpenShiftClient client;

    @Inject
    @ConfigProperty(name = "remote.proxy.image", defaultValue = "quay.io/quarkus-playpen/remote-playpen-proxy:latest")
    String proxyImage;

    @Inject
    @ConfigProperty(name = "proxy.imagepullpolicy", defaultValue = "Always")
    String proxyImagePullPolicy;

    private RemotePlaypenConfigSpec getPlaypenConfig(RemotePlaypen primary) {
        RemotePlaypenConfig config = findPlaypenConfig(primary);
        return toDefaultedSpec(config);
    }

    private RemotePlaypenConfig findPlaypenConfig(RemotePlaypen primary) {
        MixedOperation<RemotePlaypenConfig, KubernetesResourceList<RemotePlaypenConfig>, Resource<RemotePlaypenConfig>> configs = client
                .resources(RemotePlaypenConfig.class);
        String configNamespace = "quarkus";
        String configName = "global";
        if (primary.getSpec() != null && primary.getSpec().getConfig() != null) {
            configName = primary.getSpec().getConfig();
            configNamespace = primary.getMetadata().getNamespace();
            if (primary.getSpec().getConfigNamespace() != null) {
                configNamespace = primary.getSpec().getConfigNamespace();
            }
        }
        RemotePlaypenConfig config = configs.inNamespace(configNamespace).withName(configName).get();
        return config;
    }

    public static String playpenDeployment(RemotePlaypen primary) {
        return primary.getMetadata().getName() + "-playpen";
    }

    private void createProxyDeployment(RemotePlaypen primary, RemotePlaypenConfigSpec config, AuthenticationType auth) {
        String serviceName = primary.getMetadata().getName();
        String name = playpenDeployment(primary);
        String image = proxyImage;
        String imagePullPolicy = proxyImagePullPolicy;

        var container = new DeploymentBuilder()
                .withMetadata(RemotePlaypenReconciler.createMetadata(primary, name))
                .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                .withMatchLabels(Map.of("run", name))
                .endSelector()
                .withNewTemplate().withNewMetadata().addToLabels(Map.of("run", name)).endMetadata()
                .withNewSpec()
                .addNewContainer();
        if (auth == AuthenticationType.secret) {
            container.addNewEnv().withName("SECRET").withNewValueFrom().withNewSecretKeyRef().withName(playpenSecret(primary))
                    .withKey("password").endSecretKeyRef().endValueFrom().endEnv();
        }
        if (config.toExposePolicy() == ExposePolicy.ingress && config.getIngress().getDomain() == null) {
            // using a prefix
            container.addNewEnv().withName("CLIENT_PATH_PREFIX").withValue(getIngressPathPrefix(primary)).endEnv();
        }
        String logLevel = config.getLogLevel();
        if (primary.getSpec() != null && primary.getSpec().getLogLevel() != null) {
            logLevel = primary.getSpec().getLogLevel();
        }
        if (logLevel != null) {
            container.addNewEnv().withName("QUARKUS_LOG_CATEGORY__IO_QUARKIVERSE_PLAYPEN__LEVEL").withValue(logLevel)
                    .endEnv();
        }
        long idleTimeout = config.getIdleTimeoutSeconds() * 1000;

        var spec = container
                .addNewEnv().withName("SERVICE_NAME").withValue(serviceName).endEnv()
                .addNewEnv().withName("SERVICE_HOST").withValue(origin(primary)).endEnv()
                .addNewEnv().withName("SERVICE_PORT").withValue("80").endEnv()
                .addNewEnv().withName("SERVICE_SSL").withValue("false").endEnv()
                .addNewEnv().withName("IDLE_TIMEOUT").withValue(Long.toString(idleTimeout)).endEnv()
                .addNewEnv().withName("CLIENT_API_PORT").withValue("8081").endEnv()
                .addNewEnv().withName("AUTHENTICATION_TYPE").withValue(auth.name()).endEnv()
                .withImage(image)
                .withImagePullPolicy(imagePullPolicy)
                .withName(name)
                .addNewPort().withName("proxy-http").withContainerPort(8080).withProtocol("TCP").endPort()
                .addNewPort().withName("playpen-http").withContainerPort(8081).withProtocol("TCP").endPort()
                .endContainer();

        Deployment deployment = spec
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        client.apps().deployments().resource(deployment).serverSideApply();
        primary.getStatus().getCleanup().add(0, new RemotePlaypenStatus.CleanupResource("deployment", name));

    }

    public static String origin(RemotePlaypen primary) {
        return primary.getMetadata().getName() + "-origin";
    }

    private void createOriginService(RemotePlaypen primary, RemotePlaypenConfigSpec config) {
        String serviceName = primary.getMetadata().getName();
        String name = origin(primary);
        Map<String, String> selector = null;
        if (primary.getStatus() == null || primary.getStatus().getOldSelectors() == null
                || primary.getStatus().getOldSelectors().isEmpty()) {
            selector = client.services().inNamespace(primary.getMetadata().getNamespace()).withName(serviceName).get().getSpec()
                    .getSelector();
        } else {
            selector = primary.getStatus().getOldSelectors();
        }
        Service service = new ServiceBuilder()
                .withMetadata(RemotePlaypenReconciler.createMetadata(primary, name))
                .withNewSpec()
                .addNewPort()
                .withName("http")
                .withPort(80)
                .withProtocol("TCP")
                .withTargetPort(new IntOrString(8080))
                .endPort()
                .withSelector(selector)
                .withType("ClusterIP")
                .endSpec().build();
        client.resource(service).serverSideApply();
        primary.getStatus().getCleanup().add(0, new RemotePlaypenStatus.CleanupResource("service", name));
    }

    private static String playpenServiceName(RemotePlaypen primary) {
        return primary.getMetadata().getName() + "-playpen";
    }

    private void createClientService(RemotePlaypen primary, RemotePlaypenConfigSpec config) {
        String name = playpenServiceName(primary);
        ExposePolicy exposePolicy = config.toExposePolicy();
        var spec = new ServiceBuilder()
                .withMetadata(RemotePlaypenReconciler.createMetadata(primary, name))
                .withNewSpec();
        if (exposePolicy == ExposePolicy.nodePort || (primary.getSpec() != null && primary.getSpec().getNodePort() != null)) {
            spec.withType("NodePort");
        }

        var port = spec
                .addNewPort()
                .withName("http")
                .withPort(80)
                .withProtocol("TCP")
                .withTargetPort(new IntOrString(8081));

        if (primary.getSpec() != null && primary.getSpec().getNodePort() != null) {
            port.withNodePort(primary.getSpec().getNodePort());
        }

        Service service = port
                .endPort()
                .withSelector(Map.of("run", playpenDeployment(primary)))
                .endSpec().build();
        client.resource(service).serverSideApply();

        int routerTimeout = 60;

        if (exposePolicy == ExposePolicy.secureRoute) {
            String routeName = primary.getMetadata().getName() + "-playpen";
            Route route = new RouteBuilder()
                    .withMetadata(RemotePlaypenReconciler.createMetadataWithAnnotations(primary, routeName,
                            "haproxy.router.openshift.io/timeout", routerTimeout + "s"))
                    .withNewSpec().withNewTo().withKind("Service").withName(playpenServiceName(primary))
                    .endTo()
                    .withNewPort().withNewTargetPort("http").endPort()
                    .withNewTls().withTermination("edge").withInsecureEdgeTerminationPolicy("Redirect").endTls()
                    .endSpec().build();
            client.adapt(OpenShiftClient.class).routes().resource(route).serverSideApply();
            primary.getStatus().getCleanup().add(0, new RemotePlaypenStatus.CleanupResource("route", routeName));
        } else if (exposePolicy == ExposePolicy.route) {
            String routeName = primary.getMetadata().getName() + "-playpen";
            Route route = new RouteBuilder()
                    .withMetadata(RemotePlaypenReconciler.createMetadataWithAnnotations(primary, routeName,
                            "haproxy.router.openshift.io/timeout", routerTimeout + "s"))
                    .withNewSpec().withNewTo().withKind("Service").withName(playpenServiceName(primary))
                    .endTo()
                    .withNewPort().withNewTargetPort("http").endPort()
                    .endSpec().build();
            client.adapt(OpenShiftClient.class).routes().resource(route).serverSideApply();
            primary.getStatus().getCleanup().add(0, new RemotePlaypenStatus.CleanupResource("route", routeName));
        } else if (exposePolicy == ExposePolicy.ingress) {
            String ingressName = primary.getMetadata().getName() + "-playpen";
            IngressFluent<IngressBuilder>.SpecNested<IngressBuilder> ingressSpec = new IngressBuilder()
                    .withMetadata(RemotePlaypenReconciler.createMetadataWithAnnotations(primary, ingressName,
                            config.getIngress().getAnnotations()))
                    .withNewSpec();

            if (config.getIngress().getDomain() != null) {
                ingressSpec.addNewRule()
                        .withHost(ingressName + "-" + primary.getMetadata().getNamespace() + "."
                                + config.getIngress().getDomain())
                        .withNewHttp()
                        .addNewPath()
                        .withPath("/")
                        .withPathType("Prefix")
                        .withNewBackend()
                        .withNewService()
                        .withName(playpenServiceName(primary))
                        .withNewPort()
                        .withName("http")
                        .endPort().endService().endBackend().endPath().endHttp().endRule();
            } else {
                ingressSpec.addNewRule()
                        .withHost(config.getIngress().getHost())
                        .withNewHttp()
                        .addNewPath()
                        .withPath(getIngressPathPrefix(primary))
                        .withPathType("Prefix")
                        .withNewBackend()
                        .withNewService()
                        .withName(playpenServiceName(primary))
                        .withNewPort()
                        .withName("http")
                        .endPort().endService().endBackend().endPath().endHttp().endRule();
            }

            Ingress ingress = (Ingress) ingressSpec.endSpec().build();
            client.network().v1().ingresses().resource(ingress).serverSideApply();
            primary.getStatus().getCleanup().add(0, new RemotePlaypenStatus.CleanupResource("ingress", ingressName));
        }
        primary.getStatus().getCleanup().add(0, new RemotePlaypenStatus.CleanupResource("service", name));
    }

    private static String getIngressPathPrefix(RemotePlaypen primary) {
        return "/" + primary.getMetadata().getName() + "-playpen" + "-" + primary.getMetadata().getNamespace();
    }

    private boolean isOpenshift() {
        for (APIGroup group : client.getApiGroups().getGroups()) {
            if (group.getName().contains("openshift"))
                return true;
        }
        return false;
    }

    private static String playpenSecret(RemotePlaypen primary) {
        return primary.getMetadata().getName() + "-playpen-auth";
    }

    private void createSecret(RemotePlaypen playpen) {
        String name = playpenSecret(playpen);
        String password = RandomStringUtils.randomAlphabetic(10);
        Secret secret = new SecretBuilder()
                .withMetadata(RemotePlaypenReconciler.createMetadata(playpen, name))
                .addToStringData("password", password)
                .build();
        client.secrets().resource(secret).serverSideApply();
        playpen.getStatus().getCleanup().add(0, new RemotePlaypenStatus.CleanupResource("secret", name));
    }

    @Override
    public UpdateControl<RemotePlaypen> reconcile(RemotePlaypen playpen, Context<RemotePlaypen> context) {
        if (playpen.getStatus() == null) {
            log.info("reconcile");
            RemotePlaypenStatus status = new RemotePlaypenStatus();
            playpen.setStatus(status);
            try {
                ServiceResource<Service> serviceResource = client.services().inNamespace(playpen.getMetadata().getNamespace())
                        .withName(playpen.getMetadata().getName());
                Service service = serviceResource.get();
                if (service == null) {
                    status.setError("Service does not exist");
                    return UpdateControl.updateStatus(playpen);
                }
                RemotePlaypenConfig config = findPlaypenConfig(playpen);
                RemotePlaypenConfigSpec configSpec = getPlaypenConfig(playpen);
                AuthenticationType auth = configSpec.toAuthenticationType();
                if (auth == AuthenticationType.secret) {
                    createSecret(playpen);
                }
                createProxyDeployment(playpen, configSpec, auth);
                createOriginService(playpen, configSpec);
                createClientService(playpen, configSpec);

                Map<String, String> oldSelectors = new HashMap<>();
                oldSelectors.putAll(service.getSpec().getSelector());
                status.setOldSelectors(oldSelectors);
                String proxyDeploymentName = playpenDeployment(playpen);
                UnaryOperator<Service> edit = (s) -> {
                    ServiceBuilder builder = new ServiceBuilder(s);
                    ServiceFluent<ServiceBuilder>.SpecNested<ServiceBuilder> spec = builder.editSpec();
                    spec.getSelector().clear();
                    spec.getSelector().put("run", proxyDeploymentName);
                    // Setting externalTrafficPolicy to Local is for getting clientIp address
                    // when using NodePort. If service is not NodePort then you can't use this
                    // spec.withExternalTrafficPolicy("Local");
                    return spec.endSpec().build();
                };
                serviceResource.edit(edit);

                status.setCreated(true);
                if (config != null) {
                    playpen.getMetadata().getLabels().put("io.quarkiverse.playpen/config",
                            config.getMetadata().getNamespace() + "-" + config.getMetadata().getName());
                    return UpdateControl.updateResourceAndStatus(playpen);
                } else {
                    return UpdateControl.updateStatus(playpen);
                }
            } catch (RuntimeException e) {
                status.setError(e.getMessage());
                log.error("Error creating playpen " + playpen.getMetadata().getName(), e);
                return UpdateControl.updateStatus(playpen);
            }
        } else {
            return UpdateControl.<RemotePlaypen> noUpdate();
        }
    }

    private void suppress(Runnable work) {
        try {
            work.run();
        } catch (Exception ignore) {

        }
    }

    @Override
    public DeleteControl cleanup(RemotePlaypen playpen, Context<RemotePlaypen> context) {
        log.info("cleanup");
        if (playpen.getStatus() == null) {
            return DeleteControl.defaultDelete();
        }
        if (playpen.getStatus().getOldSelectors() != null && !playpen.getStatus().getOldSelectors().isEmpty()) {
            resetServiceSelector(client, playpen);
        }
        if (playpen.getStatus().getCleanup() != null) {
            for (RemotePlaypenStatus.CleanupResource cleanup : playpen.getStatus().getCleanup()) {
                log.info("Cleanup: " + cleanup.getType() + " " + cleanup.getName());
                if (cleanup.getType().equals("secret")) {
                    suppress(() -> client.secrets().inNamespace(playpen.getMetadata().getNamespace())
                            .withName(cleanup.getName())
                            .delete());
                } else if (cleanup.getType().equals("route")) {
                    suppress(() -> client.adapt(OpenShiftClient.class).routes()
                            .inNamespace(playpen.getMetadata().getNamespace())
                            .withName(cleanup.getName()).delete());
                } else if (cleanup.getType().equals("service")) {
                    suppress(() -> client.services().inNamespace(playpen.getMetadata().getNamespace())
                            .withName(cleanup.getName()).delete());
                } else if (cleanup.getType().equals("deployment")) {
                    suppress(() -> client.apps().deployments().inNamespace(playpen.getMetadata().getNamespace())
                            .withName(cleanup.getName()).delete());
                } else if (cleanup.getType().equals("ingress")) {
                    suppress(() -> client.network().v1().ingresses().inNamespace(playpen.getMetadata().getNamespace())
                            .withName(cleanup.getName()).delete());
                }
            }
        }

        return DeleteControl.defaultDelete();
    }

    public static void resetServiceSelector(KubernetesClient client, RemotePlaypen playpen) {
        ServiceResource<Service> serviceResource = client.services().inNamespace(playpen.getMetadata().getNamespace())
                .withName(playpen.getMetadata().getName());
        UnaryOperator<Service> edit = (s) -> {
            return new ServiceBuilder(s)
                    .editSpec()
                    .withSelector(playpen.getStatus().getOldSelectors())
                    .endSpec().build();

        };
        serviceResource.edit(edit);
    }

    static ObjectMeta createMetadata(RemotePlaypen resource, String name) {
        final var metadata = resource.getMetadata();
        return new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(metadata.getNamespace())
                .withLabels(Map.of("app.kubernetes.io/name", name))
                .build();
    }

    static ObjectMeta createMetadataWithAnnotations(RemotePlaypen resource, String name, String... annotations) {
        final var metadata = resource.getMetadata();
        ObjectMetaBuilder builder = new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(metadata.getNamespace())
                .withLabels(Map.of("app.kubernetes.io/name", name));
        Map<String, String> aMap = new HashMap<>();

        for (int i = 0; i < annotations.length; i++) {
            aMap.put(annotations[i], annotations[++i]);
        }
        return builder.withAnnotations(aMap).build();
    }

    static ObjectMeta createMetadataWithAnnotations(RemotePlaypen resource, String name, Map<String, String> annotations) {
        final var metadata = resource.getMetadata();
        ObjectMetaBuilder builder = new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(metadata.getNamespace())
                .withLabels(Map.of("app.kubernetes.io/name", name));
        return builder.withAnnotations(annotations).build();
    }

    /**
     * pull spec from config and set up default values if value not set
     *
     * @param config
     * @return
     */
    public RemotePlaypenConfigSpec toDefaultedSpec(RemotePlaypenConfig config) {
        RemotePlaypenConfigSpec spec = new RemotePlaypenConfigSpec();
        spec.setAuthType(AuthenticationType.secret.name());
        if (isOpenshift()) {
            spec.setExposePolicy(ExposePolicy.secureRoute.name());
        } else {
            spec.setExposePolicy(ExposePolicy.nodePort.name());
        }
        spec.setIdleTimeoutSeconds(60);

        if (config == null || config.getSpec() == null)
            return spec;

        RemotePlaypenConfigSpec oldSpec = config.getSpec();
        spec.setLogLevel(oldSpec.getLogLevel());
        if (oldSpec.getIngress() != null) {
            spec.setExposePolicy(ExposePolicy.ingress.name());
            spec.setIngress(oldSpec.getIngress());
        } else if (oldSpec.toExposePolicy() == ExposePolicy.ingress) {
            spec.setExposePolicy(ExposePolicy.ingress.name());
            spec.setIngress(new RemotePlaypenConfigSpec.PlaypenIngress());
        }
        if (oldSpec.getAuthType() != null)
            spec.setAuthType(oldSpec.getAuthType());
        if (oldSpec.getIdleTimeoutSeconds() != null)
            spec.setIdleTimeoutSeconds(oldSpec.getIdleTimeoutSeconds());
        if (oldSpec.getExposePolicy() != null)
            spec.setExposePolicy(oldSpec.getExposePolicy());
        return spec;
    }
}
