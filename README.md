# Quarkus Playpen

Many organizations have a test or development cluster that mirrors
production so that new versions of various microservices, 
web apps, and databases can be end-to-end tested.

Quarkus Playpen allows let's your local
machine look as it is a part of this development cluster
so you that you can do local development and yet
do end to end testing. It is also designed to work
within a CI/CD build as well.

Currently only HTTP-based microservice development
is supported, but, if there's enough interest in
this project we intend to look into other protocols
to support like gRPC and Kafka.

Quarkus Playpen is a lightweight, low tech solution that
only requires that your test/dev cluster's network
is visible.  For quarkus development it is built into
a quarkus extension.  For other Java or non-Java frameworks
it also has a command line executable that can run.

There are other technologies out there like Telepresence
that might be a more comprehensive solution.  Quarkus Playpen
was created to make it as simple as possible to use
and install with minimal operations requirements.

Quarkus Playpen has a Kubernetes Operator that
can be used to manage installation.  All it requires
is the ability to create, delete, and modify K8s
Services and Deployments.

Quarkus Playpen is also not limited to Kubernetes.
At it's core, it is just a HTTP proxy and and a poll-and-forward
client, so there's no reason it couldn't work on
raw AWS, Azure, or within an old-school datacenter.

# How does it work?  
Playpen is an HTTP proxy that
is installed in front of any microservice that you want
to do development with.  

If you're doing Quarkus
development, using it just requires adding
an extension to your pom and specifying a connection
URL to a port exposed by the proxy.  Quarkus
connects to the proxy over a different port, set's
up a session and polls the proxy for new requests.  Through
Quarkus's "virtual http" polled requests are put directly
on the Vertx event queue to be picked up and run with
Vertx HTTP.

If you're doing Node.js, Go, or any other language
you start a command line executable that does the same
thing as quarkus, but forwards HTTP requests to the
local process.

This polling mechanism doesn't require that the
developer laptop's IP address be visible to the
development cluster.  The server proxy's dev port
must be exposed and IP address visible to the develoment
machine.  On kubernetes this would be through
the NodePort mechanism or an ingress.  Multiple
options are available and managed by the Playpen Operator.

For Kubernetes, the Playpen Operator is not required
and your cluster admin can install Quarkus Playpen
manually.


