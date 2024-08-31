# Quarkus Playpen
## *Live coding within a Kubernetes Development Cloud*

Try out the [quickstart](sample)

Many organizations have a test or development cluster/cloud that mirrors
production.  This allows developers to test-drive their changes
against a simulated production environment.

To get something deployed in these environments requires a CI build
and a deploy to the cluster.  This can take a lot of time and seriously
slow down the development cycle.

Quarkus Playpen allows you to create a local or remote *playpen* that 
temporarily overrides an existing service so that you can do live
coding and quickly test your changes.  Requests to a sevice can be routed to your 
laptop/IDE (local playpen) or a temporary pod in the development cloud (remote playpen).  When you're done
testing, requests get routed to the original service. 

Currently only HTTP-based microservice development
is supported, but, if there's enough interest in
this project we intend to look into other protocols
to support like gRPC and Kafka.

## System Requirements
One of the goals of *Playpen* was to have a very low bar on system requirements
both operationally and on the developer's laptop.

### Cloud Requirements
* Kubernetes or Openshift
* Willingness to give CRUD permissions to the *Playpen Operator* for services, deployments,
pods, secrets, service accounts, ingresses, and openshift routes.
* Willingness to open up the Development Cloud network so that parts of it are visible to a
developer's laptop.  Visibility requirements differ between local and remote *playpens*
### Developer laptop requirements
* For Quarkus projects, only the `quarkus-playpen` extension is required
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
            <version>0.1</version>
        </dependency>
```

For non-Quarkus projects you must download the [Playpen CLI](#playpen-cli)

##Enabling a Playpen for a Service

Playpen is enabled on a per-service basis.  To create a playpen for
a specific Kubernetes service you must create a YAML file

**NOTE** It is highly recommended you set up the default
configuration policies for created playpens using a
[PlaypenConfig](#playpen-configs)

```yaml
apiVersion: "io.quarkiverse.playpen/v1"
kind: Playpen
metadata:
  name: <service-name>
```

The *service-name* within the YAML file must be equivalent to a service
deployed in the same namespace as the *playpen* you are creating.

```shell
kubectl apply -f playpen.yml
```

When this YAML is apply to your development cluster, a few things will happen
within the same namespace of the service your are creating a *playpen* for.
1. Depending on the default [authentication policy](#authentication-policy) a new
secret may be created
2. A service account will be created for the playpen
3. A role-binding will be created for the service account so that
it can do CRUD on pods in the namespace and get and list 
services and deployments
1. A Deployment will be created called `<service-name>-playpen`.  This deployment
is a Playpen Proxy and the service account created earlier will be bound to it.  This proxy sits between the original service and the local
or remote playpen of the developer that is interacting with the proxy.
2. The existing Service will have its selector changed to point to the `<service-name>-playpen`
deployment
3. A new Service will be created called `<service-name>-origin`.  This new service
will point to the original deployment that the service managed.
4. Depending on the [expose policy](#expose-policy) a new Ingress or Openshift
route may be created

The proxy exposes two ports.  The main port receives regular requests from the
service's loadbalancer and decides whether to forward the request to the original
service deployment or to a developer's playpen connection.  

The 2nd port is for developer playpen connections.
This 2nd port is exposed through the default [expose policy](#expose-policy).
Developer connections to the playpen are secured the default [authentication policy](#authentication-policy)

### Playpen YAML

The Playpen Spec allows the following values
* **config** Name of a [PlaypenConfig](#playpen-configs) to use
for configuration
* **configNamespace** Namespace that the config lives in
* **nodePort** If the [expose policy](#expose-policy) is Nodeport, this is a 
a specific port value to assign to the node port.
* **logLevel** Set to "DEBUG" if you are having issues with Playpen.  This will
turn on debugging logging in the Playpen Proxy that you can view.



## Playpen Configs
Playpen Configs are Kubernetes records that define the default
configuration for created Playpens.

**NOTE** It is highly recommended that you create a PlaypenConfig
within the `quarkus` namespace calls `global`.

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

### Authentication Policy
The `spec.authType` defines how playpen proxy connections
are secured.  These values are supported

* **none** No credentials are required when connecting to the playpen
* **secret** A secret will be created within the same namespace as the
playpen.  The name of the secret will be `<service-name>-playpen-auth`.
Developer connections must use this credential when connecting to the playpen.
* **openshiftBasicAuth** When on Openshift, you can use a cluster user and 
password to authenticate developer playpen connections.

The default authentication policy is `secret`.

### Expose Policy
The `spec.exposePolicy` defines how the 2nd port on the
Playpen Proxy is exposed so that developers can make
connections to it.

* **manual** The admin is responsible for exposing the 2nd port on the
Playpen Proxy.  
* **nodePort** A random node port will be used for exposing the port.
* **ingress**  An ingress will be used.  See [ingress settings](#ingress-settings) for more info
* **route** On Openshift clusters, a route will be created with the name
`<service-name>-playpen`
* **secureRoute** On Openshift clusters, a secure will will be reated with the name
  `<service-name>-playpen`


#### Ingress Settings
The `spec.ingress` allows you to specify how an ingress
will be set up for the developer connection port.

* **host**  This specifies the exact host of the ingress.  In this case
an ingress will be created with a path prefix on the host that
forwards to the developer connection port.  This prefix will be
`<service-name>-playpen-<namespace>`
* **domain** In this case, an ingress will be created for the
playpen developer connection on the DNS name `<service-name>-playpen-<namespace.<domain>`
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

## Local Playpens

Local playpens allow you to route requests meant for the
original service, down to your laptop.

## Local Playpen Requirements
* The 2nd port that is used for developer playpen connections
must be set up using an [expose policy](#expose-policy)
* The development cluster must expose any databases or services
that local service needs to connect to.  If this is not possible
try using a [remote playpen](#remote-playpens) instead.
* The developer must know the credentials needed to connect
to the playpen defined by the [authentication policy](#authentication-policy)

### Connection URL

The connection URL is needed by Quarkus or the [playpen cli](#playpen-cli)
to connect to the playpen proxy and start a playpen for development.
The scheme, host, port, and base path prefix are defined by the
[expose policy](#expose-policy).

`http[s]://<expose-policy-host-port/<possible-prefix-if-ingress>/local/<who-ami-i>`

Notice where `local` is on the path.  This is important as it tells
the proxy that you are creating a local playpen connection.

The `who-ami-i` part of the path is also required.  This should 
be the first name or something that identifies the human developer making
the connection.  Basically `who-ami-i` is the name of the playpen
session.

The connection URL also can define connection parameters.

`http[s]://foo/local/john?global=true`

#### Local Config parameters

* **global** - **true|false**.  If `true`, all requests to the 
service will be sent to the developer's laptop.  
* **path** - **path-prefix**.  If set, and `global=false` the playpen
proxy will route requests to the developer's laptop if the 
HTTP request has the path prefix specified
* **query** - **\<name>=\<value>**  If set and `global=false` the playpen
  proxy will route requests to the developer's laptop if the
  HTTP request has a query parameter with name and value.
* **header** - **\<name>=\<value>**  If set and `global=false` the playpen
  proxy will route requests to the developer's laptop if the
  HTTP request has a header with a specific value
* **clientIp** - **client-ip-address** If set and `global=false` the playpen
  proxy will route requests to the developer's laptop if the client ip address
matches.

Multiple session matches can be defined within the connection string

If `global=false`, by default, if the `X-Playpen-Session` header or cookie
is set within the request with a value of `who-ami-i`, then the request will
be routed to the developer's laptop.

### Examples
These examples are connecting to a playpen for a service named `greeting`
deployed in the `default` namespace.

Example URL connection string if there's a nodeport used.  All requests
will be sent to the developer's laptop

`http://192.168.49.2:32007/local/john?global=true`

Example URL connection string if there's an ingress with
`host` of `devcluster` used.  Requests that contain the `X-Playpen-Session`
with a value of `john` will be routed to the developer's laptop

`http://devcluster/greeting-playpen-default/local/john`

Example URL connection string if there's an ingress with
`domain` of `devcluster` used.  Requests that contain a query parameter
`user` with a value of `joe` will be routed to the developer's laptop.
Also requests that contain a path prefix of `users` will be rerouted.

`http://greeting-playpen-default.devcluster/local/john?query=user=joe&path=/users`

### Start the connection for Quarkus

```shell
mvn quarkus:dev -Dquarkus.playpen.local="http://devcluster/greeting-playpen-default/local/john" \
    -Dquarkus.playpen.credentials="mysecret"
```

The `quarkus.playpen.credentials` does not have to be set if there is no
authentication policy for the playpen.

This will build the app, make a connection to the playpen proxy, and start
quarkus in dev mode.  When ending the dev mode session, the playpen connection
will be disconnected and removed.

### Start the connection with the Playpen CLI

```shell
playpen local connect --credentials=mysecret --local-port=8080 http://192.168.49.2:32007/local/john?global=true
```
`credentials` does not have to be specified if there is no authentication policy
set up for the playpen

`local-port` is the port your local service is running on within your laptop.  If not specified
it defaults to `8080`

## Remote Playpens
Remote playpens are different than [local playpens](#local-playpens) in that
instead of routing requests to your laptop, requests can be routed to a 
different pod that is running within your development cluster.

If you are using Playpen with Quarkus, this pod can be automatically created
for you when you start your `quarkus:remote-dev` session. And cleaned up
automatically when you are finished with your `quarkus:remote-dev` session.

If you are not using Playpen with Quarkus you will have to create your
development pod manually.

### Connection URL

The connection URL is needed by Quarkus or the [playpen cli](#playpen-cli)
to connect to the playpen proxy and start a playpen for development.
The scheme, host, port, and base path prefix are defined by the
[expose policy](#expose-policy).

`http[s]://<expose-policy-host-port/<possible-prefix-if-ingress>/remote/<who-ami-i>`

Notice where `remote` is on the path.  This is important as it tells
the proxy that you are creating a remote playpen connection.

The `who-ami-i` part of the path is also required.  This should
be the first name or something that identifies the human developer making
the connection.  Basically `who-ami-i` is the name of the playpen
session.

The connection URL also can define connection parameters.

`http[s]://foo/remote/john?global=true`

#### Remote connection parameters

Remote playpens have the same connection parameters available as [local playpens](#local-config-parameters)

There are these additional parameters you can specify

* **cleanup** - **true|false** If `true`, when the playpen is disconnected, the playpen proxy will try
and delete the pod used for development in the cluster.
* **host** - **host[:port]** The host (and port) of a pod or service running in the development cluster
that you want requests rerouted to.  If not set, the proxy will look for a pod named `<service-name>-playpen-<who-ami-i>`.


If `host` is specified, `cleanup` will always be ignored!!

### Start the connection for Quarkus
For Quarkus, remote playpens allow you to do a full `quarkus:remove-dev`
session.

Remote playpens are a bit different with Quarkus.  You must
run `quarkus:remove-dev` to be able to set up a connection.  If you
want to run a full remote dev session, then the connection string is split
between the `quarkus.live-reload.url` and `quarkus.playpen.remote` system
properties.  If there are no config parameters, you must set
`quarkus.playpen.remote` with an empty value.

If the `host` config parameter is not set, then Quarkus will check to see
if a temporary pod already exists with the name `<service-name>-playpen-<who-ami-i>`
if it doesn't, it will zip up your compiled code and upload the zip
to the playpen proxy. The playpen proxy will then create a temporary
pod with the uploaded code and run that container as a live reload session.
When the remove dev session is over (you hit <ctrl-c>) the playpen session
will disconnect and the proxy will delete the temporary pod.

Specify the `cleanup=true` config parameter if you do not want quarkus to
delete the pod when it disconnects and ends the remote dev session.

If you are not doing a full remove dev session, then do not specify the
`quarkus.live-reload.url` and instead the `quarkus.playpen.remote` property
must have a full URL to make the connection.

**NOTE** It is up to you to manage the `quarkus.live-reload.password` property.
Playpen does not manage it.

Examples:

Here we have set up a playpen exposed with an ingress with
`host` of `devcluster` used.  Requests that contain the `X-Playpen-Session`
with a value of `john` will be routed to the developer's laptop.

Since no `host` parameter is specified, Quarkus will try and ask the
Playpen Proxy to create a temporary pod.

Notice that `quarkus.playpen.remote` has an empty value set.

```shell
mvn quarkus:remote-dev -Dquarkus.live-reload.url="http://devcluster/greeting-playpen-default/remote/john" \
     -Dquarkus.playpen.remote="" -Dquarkus.playpen.credentials="mysecret"
```

Next example, does not run in full remove dev mode and asks
the playpen to reroute requests to an existing pod running within the
dev cluster.

```shell
mvn quarkus:remote-dev -Dquarkus.playpen.remote="http://devcluster/greeting-playpen-default/remote/john?global=true&host=10.244.0.253" \
     -Dquarkus.playpen.credentials="mysecret"
```

## Playpen CLI
CLI is available here [here](/not/yet/available)



