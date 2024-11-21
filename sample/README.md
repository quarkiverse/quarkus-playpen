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

## Setup Namespace

```shell
kubectl create namespace samples
kubectl config set-context --current --namespace=samples
```

## Build Project and Image

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
kubectl apply -f https://raw.githubusercontent.com/quarkiverse/quarkus-playpen/main/operator/playpenconfigs-crd.yml
kubectl apply -f https://raw.githubusercontent.com/quarkiverse/quarkus-playpen/main/operator/playpens-crd.yml
kubectl apply -f https://raw.githubusercontent.com/quarkiverse/quarkus-playpen/main/operator/operator.yml
```

## Enable Playpen for Service

Then create a playpen for your greeting service with the
[playpen file](playpen.yml)
```shell
kubectl apply -f playpen.yml
```

You now have enabled Playpen for your service.  
Let's take a look at what was created.

```shell
kubectl get services
kubectl get deployments
```
You can see that some additional deployments and services were created.
See [docs](../README.md) for more info.

## Perform Live Coding Locally

```shell
mvnw quarkus:dev -Dplaypen.local.connect="greeting -hijack"
```

Make a change to the project's code.  Go to the URL of the greeting
service deployed in minikube.  The request will be handled by your local
quarkus:dev session!  End quarkus:dev mode and refresh your browser.
You'll see that the old service is now handling requests again.

This example hijacks all requests that are sent to the service.
See [docs](../README.md) on how it is possible
to create a playpen that is specific to your development session.










