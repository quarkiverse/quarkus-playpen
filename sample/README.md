# Using Playpen

This project is a simple Java/Quarkus REST service.  This
guide walks you through building and deploying it on Minikube
and doing live coding with it through Playpen.

## Requirements

* Minikube 1.33.1 (Although older versions probably work)
* JDK 17 or higher
* docker

## Start Minikube and Set Environment

```shell
minikube start
eval $(minikube docker-env)
```

## Build Project and IMage

```shell
mvnw clean package -Dquarkus.container-image.build=true
```

## Deploy to Kubernetes

```shell
kubectl apply -f minikube.yml
kubectl get services
echo $(minikube ip)
```

In your browser, you should be able to go to http://\`minikube ip`:30507/hello
and see the greeting service respond.  Check out the
[code](src/main/java/org/acme/GreetingResource.java#L26) and notice that the
GREETING_ENV environment variable is used in creating the greeting
message.

## Install Playpen Into Minikube

Make sure quay.io is reachable as an image repository.

```shell
kubectl apply -f https://raw.githubusercontent.com/quarkiverse/quarkus-playpen/0.9.5/operator/playpenconfigs-crd.yml
kubectl apply -f https://raw.githubusercontent.com/quarkiverse/quarkus-playpen/0.9.5/operator/playpens-crd.yml
kubectl apply -f https://raw.githubusercontent.com/quarkiverse/quarkus-playpen/0.9.5/operator/operator.yml
```

## Setup Playpen default configuration

Take a look at this [default config](playpen-default-config.yml).


Execute it
```shell
kubectl apply -f playpen-default-config.yml
```

Then create a playpen for your greeting service with the
[playpen file](playpen.yml)
```shell
kubectl apply -f playpen.yml
```

You now have enable Quarkus Playpen for your service.  
Let's take a look at the what was created.

```shell
kubectl get services
kubectl get deployments
```
You can see that some additional deployments and services were created.
See [docs](../README.md) for more info.

## Perform Live Coding Locally

```shell
kubectl get service greeting-playpen
```

Look for the Nodeport under the "Ports" column.  You'll
need that port to connect to the greeting service playpen proxy.

```shell
mvnw quarkus:dev -Dquarkus.playpen.local="http://`minikube ip`:32233/local/john?hijack=true"
```

Replace `32233` with the nodeport of the `greeting-playpen` service.  Replace
`john` with your first name.  The log should show that
you've connected with the playpen and that your project has
started in dev mode.

Make a change to the project's code.  Go to the URL of the greeting
service deployed in minikube.  The request will be handled by your local
quarkus:dev session!  End quarkus:dev mode and refresh your browser.
You'll see that the old service is now handling requests again.

There are more ways to configure and run a local playpen.
Specifically you can set up a session.  See [docs](../README.md) for
more details.










