# Quarkus Playpen
## *Live coding within a Kubernetes Development Cloud*

Try out the [quickstart](sample)

Many organizations have a test or development cluster/cloud that mirrors
production.  This allows developers to test-drive their changes
against a simulated production environment.

To get something deployed in these environments requires a CI build
and deploy to the cluster.  This can take a lot of time and seriously
slow down the development cycle.

Quarkus Playpen allows you to create a local or remote *playpen* that 
temporarily overrides an existing service so that you can do live
coding and quickly test your changes.  Requests to a service can be routed to your 
laptop/IDE (local playpen) or a temporary pod in the development cloud (remote playpen).  When you're done
testing, requests get routed back to the original service. 

Currently only HTTP-based microservice development
is supported, but, if there's enough interest in
this project we intend to look into other protocols
to support like gRPC and Kafka.

## System Requirements
One of the goals of *Playpen* was to have a very low bar on system requirements
both operationally and on the developer's laptop.

### Cloud Requirements
* Kubernetes or Openshift
* Installation of the Playpen Operator
* Willingness to give CRUD permissions to the *Playpen Operator* for services, deployments,
pods, secrets, service accounts, ingresses, and openshift routes.
### Developer laptop requirements
* Works best if developer's laptop has [kubectl port forwarding](https://kubernetes.io/docs/tasks/access-application-cluster/port-forward-access-application-cluster/) permissions
* Works best if developer has logged in via *kubectl* or *oc*
* For Quarkus projects, the `quarkus-playpen` extension is required
* For other languages and Java Frameworks, the [Playpen CLI](#playpen-cli) is required

## Installation

### Dev Cloud Installation
Make sure quay.io is reachable as an image repository.

```shell
kubectl apply -f https://raw.githubusercontent.com/quarkiverse/quarkus-playpen/main/operator/playpenconfigs-crd.yml
kubectl apply -f https://raw.githubusercontent.com/quarkiverse/quarkus-playpen/main/operator/playpens-crd.yml
kubectl apply -f https://raw.githubusercontent.com/quarkiverse/quarkus-playpen/main/operator/operator.yml
```

This will install some CRDs and start the *Playpen Operator* under the `quarkus`
namespace.

### Developer Laptop Installation

For quarkus projects, add this dependency to your project
```xml
        <dependency>
            <groupId>io.quarkiverse.playpen</groupId>
            <artifactId>quarkus-playpen</artifactId>
            <version>999-SNAPSHOT</version>
        </dependency>
```

For non-Quarkus projects you must download the [Playpen CLI](#playpen-cli)

## Enabling a Playpen for a Service

Playpen is enabled on a per-service basis.  To create a playpen for
a specific Kubernetes service you must create a YAML file

**NOTE** By default, connecting to a playpen will require no
authentication and the developer must have kubernetes port forwarding permission.
To set up an authentication policy or modify other configuration
options, there is another CRD you can apply that sets up default
configuration policies.  See [PlaypenConfig](#playpen-configs).

```yaml
apiVersion: "io.quarkiverse.playpen/v1"
kind: Playpen
metadata:
  name: <service-name>
```

The *service-name* within the YAML file must be equivalent to a service
deployed in the same namespace as the *playpen* you are creating.

Whoever creates the playpen must have permissions to create the Playpen CRD.

```shell
$ kubectl apply -f playpen.yml
```

When this YAML is applied to your development cluster, a few things will happen
within the same namespace of the service your are creating a *playpen* for.
1. Depending on the default [authentication policy](#authentication-policy) a new
secret may be created
2. A service account will be created for the playpen
3. A role-binding will be created for the service account so that
it can do CRUD on pods in the namespace and get and list 
services and deployments
4. A Deployment will be created called `<service-name>-playpen`.  This deployment
is a Playpen Proxy and the service account created earlier will be bound to it.  This proxy sits between the original service and the local
or remote playpen of the developer that is interacting with the proxy.
5. The existing Service will have its selector changed to point to the `<service-name>-playpen`
deployment
6. A new Service will be created called `<service-name>-origin`.  This new service
will point to the original deployment that the service managed.
7. Depending on the [expose policy](#expose-policy) a new Ingress or Openshift
route may be created

The proxy exposes two ports.  The main port receives regular requests from the
service's loadbalancer and decides whether to forward the request to the original
service deployment or to a developer's playpen connection.  

The 2nd port is for developer playpen connections.
This 2nd port is exposed through the default [expose policy](#expose-policy).
Developer connections to the playpen are secured the default [authentication policy](#authentication-policy)

Deleting the playpen CRD will delete all created resources and restore
the service to its original state.

### Playpen YAML

The Playpen Spec allows the following values
* **config** Name of a [PlaypenConfig](#playpen-configs) to use
for configuration.  Defaults to `global`
* **configNamespace** Namespace that the config lives in.  Defaults to `quarkus`
* **nodePort** If the [expose policy](#expose-policy) is Nodeport, this is a 
a specific port value to assign to the node port.
* **logLevel** Set to "DEBUG" if you are having issues with Playpen.  This will
turn on debugging logging in the Playpen Proxy that you can view.

### Troubleshooting
If you are having problems, take a look at the status of the playpen by doing:
```shell
$ kubectl get playpen <service-name> -o yaml
```

Example output:

```yaml
status:
    authPolicy: none
    cleanup:
    - name: greeting-playpen
      type: service
    - name: greeting-playpen
      type: ingress
    - name: greeting-origin
      type: service
    - name: greeting-playpen
      type: deployment
    - name: greeting-playpen
      type: rolebinding
    - name: greeting-playpen
      type: serviceaccount
    created: true
    exposePolicy: ingress
    ingress: devcluster/greeting-playpen-default
    oldSelectors:
      app.kubernetes.io/name: greeting
```
The `created` variable will be `true` if the `Playpen` was created successfully.  If
there was a problem an `error` variable will be populated with an error message.  You can look
at the `quarkus-playpen-operator` logs for more information.

The [auth policy](#authentication-policy) used will
be specified under `authPolicy`.  The [expose policy](#expose-policy) under `exposePolicy`.
If an ingress was created, the `ingress` string will contain the host and prefix path(if needed).


The `cleanup` variable will
contain a list of things that were created and that will be deleted if the `Playpen` is ever 
deleted.  The `oldSelectors` variable shows the old selector value of the original `Service`
When the `Playpen` is deleted, the selector will be set back to the old value.

## Local vs. Remote Playpens

*Local* playpens allow you to route requests meant for the
original service, down to your laptop.  This allows you to develop
your service locally.

*Remote* playpens differ from [local playpens](#local-playpens) in that
instead of routing requests to your laptop, requests can be routed to a
different pod that is running within your development cluster.

*Local* playpens offer an ideal development experience, but depending on
how locked down your development cluster is, you might not be able to run
them and a *Remote* playpen might be a better option.


## Local Playpens

Local playpens allow you to route requests meant for the
original service, down to your laptop.  This allows you to develop
your service locally.

## Local Playpen Requirements
* The developer must have [kubectl port forwarding](https://kubernetes.io/docs/tasks/access-application-cluster/port-forward-access-application-cluster/) permissions
  or or the playpen proxy's 2nd port must be visible by setting up an [expose policy](#expose-policy)
* The developer must set up port forwards for any dependent databases
  or services...or the development cluster must expose those databases 
  and services.  If this is not possible
  try using a [remote playpen](#remote-playpens) instead.
* The developer must know the credentials needed to connect
  to the playpen defined by the [authentication policy](#authentication-policy), unless no auth mechanism is set up
* Works best if the developer is already logged in via `kubectl` or `oc`. Otherwise
  kubernetes/openshift credentials may have to be provided (i.e. a token).
* For Quarkus projects, the `quarkus-playpen` extension is required
* For other languages and non-Quarkus Java Frameworks, the [Playpen CLI](#playpen-cli) is required


## Connecting to a local playpen
*NOTE* Make sure you have done the [installation requirements](#developer-laptop-installation). 

To setup a local playpen with Quarkus projects, you must run in Quarkus Dev Mode.

```shell
$ mvn quarkus:dev -Dplaypen.local.connect="greeting -hijack"
```

With any other language or Java Framework, you must use the [Playpen CLI](#playpen-cli).
You must also manually start your service locally.

```shell
$ playpen local connect greeting -hijack
```

The CLI parameters and switches are almost exact between Quarkus and the
Playpen CLI.

Using `-hijack` means that all requests sent to the service will be re-routed to your laptop.
While easy to set up, this means that every single request sent to your service in our development
cluster will be routed to your laptop.   This means that concurrent development cannot occur.

Without `-hijack`, only requests that have the `X-Playpen-Session` HTTP cookie or request header 
set to the `who` parameter of your connection will be routed to your laptop.  
The identity of `who` parameter defaults to the username of your shell.  This allows for concurrent
development on the service, but requires that you set this header/cookie value somehow.  There
are other ways to do conditional routing to your laptop that is [discussed later](#conditional-routing).

## Connection Parameters

### Connection Target

The *connection target* is required and specifies the service name
or connection URL for the playpen.  

If your developer has kubectl permissions
to view services and do port forwarding, then you can specify the name of the 
Kubernetes Service, `[namespace/]service`.  If you have set up a default
namespace for your shell, then you do not need to specify the namespace.

```shell
$ mvn quarkus:dev -Dplaypen.local.connect="myproject/greeting -hijack"
$ playpen local connect myproject/greeting -hijack
```

When you specify the connection parameter as a service name, a port forward
on your laptop will be set up that routes to the 2nd port of the playpen proxy
deployed in your cluster.  Quarkus or the Playpen CLI will do this automatically,
but it will need the appropriate Kubernetes/Openshift credentials.  These credentials
are already set up if the developer uses the cli `kubectl` or `oc`.  Otherwise
please see the CLI switches for [kubernetes setup](#kube-cli).

If your developer laptop does not have the appropriate kubernetes credentials
or permissions, then the playpen proxy must expose the connection port via an [expose policy](#expose-policy) 
and you must specify an HTTP URL `connection parameter`

```shell
$ mvn quarkus:dev -Dplaypen.local.connect="https://greeting.devcluster -hijack"
$ playpen local connect https://devcluster/greeting -hijack
```

### Who -w, -who, --who

The `-w`, `-who`, `--who` flag provides some identity for the developer's playpen connection.
It defaults to whatever the shell's username is.

```shell
$ mvn quarkus:dev -Dplaypen.local.connect="https://greeting.devcluster -hijack -who john"
$ playpen local connect https://devcluster/greeting -hijack --who=bill
```
### Credentials -c, --credentials

If an [authentication policy](#authentication-policy) is set up, then this is where you specify the credentials.
If authentication is username/password based, then it takes the form of `username:password`.

```shell
$ mvn quarkus:dev -Dplaypen.local.connect="greeting -hijack --credentials=geheim"
$ playpen local connect https://devcluster/greeting -hijack -c bill:passwd
```

### Trust certs -trustCert, --trustCert

If the [expose policy](#expose-policy) requires connection through an `https` url, and the
server certs are self signed, you can use the `-trustcerts` flag to trust any cert the server
gives the playpen client.

## Conditional Routing Parameters

Without `-hijack`, only requests that have the `X-Playpen-Session` HTTP cookie or request header
set to the `who` parameter (defaults to your username) of your connection will be routed to your laptop.  
The identity of `who` parameter defaults to the username of your shell.  This allows for concurrent
development on the service, but requires that you set this header/cookie value somehow.  

This can be problematic as you might not have a way to set this cookie/header.  Playpen provides
alternative ways you can do conditional routing.  When you connect, you can tell the proxy
to look at various pieces of the request to determine whether or not to route to your laptop.

### Client IP Address -clientIp, -ip

You can specify that only client connections from a specific IP adress
will route requests to your laptop.  The value of the IP address defaults
to your developer laptop.  *NOTE* This only works if your kubernetes
cluster correctly sets the client IP address.

```shell
$ mvn quarkus:dev -Dplaypen.local.connect="greeting -clientIp"
$ playpen local connect greeting -ip 10.10.2.100
```

### URL Path Prefix -path, -p

You can specify a path prefix.  If the request URL matches this path
prefix, then requests will be routed to your laptop

```shell
$ mvn quarkus:dev -Dplaypen.local.connect="greeting -p /foo/bar"
$ playpen local connect greeting --path=/foo/bar
```

### URL Query parameter -query, -q

You can specify a query parameter and the value of that query parameter.
If it is a match, then requests will be routed to your laptop.

```shell
$ mvn quarkus:dev -Dplaypen.local.connect="greeting -q color=green"
```

So the url `/foo?color=green` would result in a conditional route.

If you do not specify the value of the query parameter, then the conditional
route will trigger if the query parameter is present.

```shell
$ playpen local connect greeting --query=color
```

### Arbitrary header or cookie -header

You can specify a cookie/header and the value of that cookie/header.
If it is a match, then requests will be routed to your laptop.

```shell
$ playpen local connect greeting -header X-Username=bill
```

### One request at a time, -onPoll

This only works with local playpen connections.  This is similar to `-hijack`.
Only one request at a time will be routed to the developer's laptop.
If the developer's laptop is processing requests, all other requests will
be routed to the original service.  This allows your laptop to not be overloaded
with requests if your service is a busy one.

## Port forwards to dependent resources, -port-forward -pf

You can tell playpen to set up a port forward to any dependent resources
(dbs, other services) that your local service needs to run.
The appropriate Kubernetes/Openshift credentials and permissions are required.  
Credentials
are already set up if the developer uses the cli `kubectl` or `oc`.  Otherwise
please see the CLI switches for [kubernetes setup](#kube-cli).  The
permissions required are view on services/pods and port forwarding.

The forward take the form of `[service|pod/][namespace/]name:[service port]:[local port]`
If you are forwarding to a pod, then you must prefix the connection string
with `pod` otherwise it defaults to `service`.  The `service port` only
needs to be specified if the service exposes multiple ports.  The `local port`
is the port you want to use on your laptop.

```shell
$ playpen local connect greeting -pf pod/dbs/greetingdb::9090
```

The above sets up a port forward to `localhost:9090` to the pod running
in the `dbs` namespace with the name `greetingdb`.


```shell
$ mvn quarkus:dev -Dplaypen.local.connect="greeting -port-forward message::8090"
```

The above sets up a port forward to `localhost:8090` to the service
`message` running in whatever the current namespace is.

## Remote Playpens
Remote playpens are different than [local playpens](#local-playpens) in that
instead of routing requests to your laptop, requests can be routed to a
different pod that is running within your development cluster.

If you are using Playpen with Quarkus, this pod can be automatically created
for you when you start your `quarkus:remote-dev` session. And cleaned up
automatically when you are finished with your `quarkus:remote-dev` session.

If you are not using Playpen with Quarkus you will have to create your
development pod manually.

## Remote Requirements
* The developer must have [kubectl port forwarding](https://kubernetes.io/docs/tasks/access-application-cluster/port-forward-access-application-cluster/) permissions
  or the playpen proxy's 2nd port must be visible by setting up an [expose policy](#expose-policy)
* The developer must know the credentials needed to connect
  to the playpen defined by the [authentication policy](#authentication-policy), unless no auth mechanism is set up
* Works best if the developer is already logged in via `kubectl` or `oc`. Otherwise
  kubernetes/openshift credentials may have to be provided (i.e. a token).
* For Quarkus projects, the `quarkus-playpen` extension is required
* For other languages and non-Quarkus Java Frameworks, the [Playpen CLI](#playpen-cli) is required
* Non-quarkus apps, those using the playpen cli, must create their own
  remote development pod.  Quarkus though, will create one for you if does not exist.

## Starting a Remote Playpen With Quarkus

Starting a remote playpen differs between Quarkus and the [Playpen CLI](#playpen-cli).
For quarkus, you are able to use `quarkus:remote-dev` mode and
additional features are available.

There are different ways to use a remote playpen with `quarkus:remote-dev` mode depending
on whether you have the ability to do [kubectl port forwarding](https://kubernetes.io/docs/tasks/access-application-cluster/port-forward-access-application-cluster/), and if not,
what your [expose policy](#expose-policy) is.

When using with quarkus in a project, you can have your local
quarkus instance interact with the playpen server to temporarily
create a pod for you using the code of the project in your laptop.
When the `quarkus:remote-dev` session is start, your laptop code will
be uploaded to the playpen server and used to create a temporary
pod.  Requests will be routed to this temporary pod.  
When the remote dev session is over (you hit <ctrl-c>) the playpen session
will disconnect and the proxy will terminate and delete the temporary pod.

```shell
$ mvn quarkus:remote-dev -Dplaypen.remote.connect="greeting -hijack"
```

Remote playpen supports the same [connection parameters](#connection-parameters) as
local playpens.  In the above example, the code if the project will be uploaded to
a temporary pods and all requests to the service will be rerouted to this temporary pod.
You can also do [conditional routing](#conditional-routing-parameters) as well.

If you do not want the temporary pod to be terminated, you can specify the `-cleanup` parameter

```shell
$ mvn quarkus:remote-dev -Dplaypen.remote.connect="greeting -hijack --cleanup=false"
```

If you do not want to have `quarkus:remote-dev` mode upload your code and create a 
temporary pod, you can set up a remote playpen that points to an existing pod using the
`-host` parameter. The `-host` parameter takes a value of `[namespace/]pod name`

```shell
$ mvn quarkus:remote-dev -Dplaypen.remote.connect="greeting -hijack -host greeting-pod"
```
If the `host` parameter is not set, then Quarkus will check to see
if a temporary pod already exists with the name `<service-name>-playpen-<who>`
if it doesn't, it will create a temporary pod


### Live Reload

If you want to do a `quarkus:remote-dev` live reload session and your development
environment supports [kubectl port forwarding](https://kubernetes.io/docs/tasks/access-application-cluster/port-forward-access-application-cluster/), then the `quarkus.live-reload.url`
will specify the port to forward to.  For example

```shell
$ mvn quarkus:remote-dev -Dquarkus.live-reload.url="http://localhost:33533" -Dplaypen.remote.connect="greeting -hijack"
```
In the above example, quarkus will set up a port forward to the temporary pod under
`localhost:33533`.

If you can't use port forwarding and have an [expose policy](#expose-policy) set up,
then your live-reload url must point to the playpen proxy of your service and you must
specify a path to the URL of `/remote/<who>` where `who` is your username or whatever
you have specify with the `-who` parameter.  The `playpen.remote.connect` must also
not specify a [connection target](#connection-target).

```shell
$ mvn quarkus:remote-dev -Dquarkus.live-reload.url="http://meeting.devcluster/remote/john" -Dplaypen.remote.connect="-hijack"
```

In this mode, the playpen proxy acts as a middle man between the developer's laptop
and the temporary pod.

### Other Quarkus Specific commands

There are a few other commands you can execute with a quarkus project for remote playpens.

You can create a temporary pod with your project's code.  This
is useful if you want to reuse the temporary pod between development
sessions.

```shell
$ mvn quarkus:remote-dev -Dplaypen.remote.create
```

You can delete this temporary pod

```shell
$ mvn quarkus:remote-dev -Dplaypen.remote.delete
```

You can see if the temporary pod exists

```shell
$ mvn quarkus:remote-dev -Dplaypen.remote.exists
```
You can get the pod name of the temporary pod 

```shell
$ mvn quarkus:remote-dev -Dplaypen.remote.get
```

## Playpen Configs
Playpen Configs are Kubernetes records that define the default
configuration for created Playpens.  If you are using *PlaypenConfigs*
you MUST create them before creating a *Playpen*.  Creating or modifying
a *PlaypenConfig* does not change playpens that use those configurations.
Those playpens must be deleted and re-created to obtain the configuration
changes.

When a Playpen CRD is applied, by default it will look for a *PlaypenConfig*
within the `quarkus` namespace called `global`.  

```yaml
apiVersion: "io.quarkiverse.playpen/v1"
kind: PlaypenConfig
metadata:
  name: global
  namespace: quarkus
spec:
  authType: none
  exposePolicy: ingress
  ingress:
    host: devcluster
```

And apply it to the dev cluster

```shell
kubectl apply -f config.yml
```

**NOTE** If you modify a `PlaypenConfig`, you must delete and re-create every playpen
that uses it if you want those playpens to use the new configuration.  Any `Playpen`
created will be labeled with the configuration that it was created with under
the label `io.quarkiverse.playpen/config` with a value of `<namespace>-<config-name`.

So, you if you wanted to delete all `Playpens` that used the `global` config in the
`quarkus` namespace, you could do this.
```shell
kubectl delete playpens -l "io.quarkiverse.playpen/config=quarkus-global"
```

### Authentication Policy
The `spec.authType` defines how playpen proxy connections
are secured.  These values are supported

* **none** No credentials are required when connecting to the playpen
* **secret** A secret will be created within the same namespace as the
playpen.  The name of the secret will be `<service-name>-playpen-auth`.
Developer connections must use this credential when connecting to the playpen.
* **openshiftBasicAuth** When on Openshift, you can use a cluster user and 
password to authenticate developer playpen connections.

The default authentication policy is `none`.

To get the value of a secret created for a service (let's say the service is `greeting`)
```shell
kubectl get secret greeting-playpen-auth -o jsonpath='{.data.password}' | base64 --decode
```

### Expose Policy
If the developer does not have kubectl port forwarding permission,
they can still connect to a playpen if the playpen's port
is exposed.  The `spec.exposePolicy` defines how the 2nd port on the
Playpen Proxy is exposed so that developers can make
connections to it.

* **none** The playpen connection port will not be exposed .  
* **nodePort** A random node port will be used for exposing the port.
* **ingress**  An ingress will be used.  See [ingress settings](#ingress-settings) for more info
* **route** On Openshift clusters, a route will be created with the name
`<service-name>-playpen`
* **secureRoute** On Openshift clusters, a secure will will be created with the name
  `<service-name>-playpen`

The default expose policy is `none`.

#### Ingress Settings
The `spec.ingress` allows you to specify how an ingress
will be set up for the developer connection port.

* **host**  This specifies the exact host of the ingress.  In this case
an ingress will be created with a path prefix on the host that
forwards to the developer connection port.  This prefix will be
`<service-name>-playpen-<namespace>`
* **domain** In this case, an ingress will be created for the
playpen developer connection on the DNS name `<service-name>-playpen-<namespace>.<domain>`
* **annotations** This is a set of annotations you want to apply to the ingress
you are creating.

Example Host setting:
```yaml
apiVersion: "io.quarkiverse.playpen/v1"
kind: PlaypenConfig
metadata:
  name: global
  namespace: quarkus
spec:
  authType: none
  exposePolicy: ingress
  ingress:
    host: devcluster
```

If the playpen/service is named `greeting`, the playpen developer connection will be available at
`http[s]://devcluster/greeting-playpen-default`

Example Domain setting:
```yaml
apiVersion: "io.quarkiverse.playpen/v1"
kind: PlaypenConfig
metadata:
  name: global
  namespace: quarkus
spec:
  authType: none
  exposePolicy: ingress
  ingress:
    domain: devcluster
```

If the playpen/service is named `greeting`, the playpen developer connection will be available at
`http[s]://greeting-playpen-default.devcluster`

## Playpen CLI
CLI switches are the same as quarkus ones defined above.



