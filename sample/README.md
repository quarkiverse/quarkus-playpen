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
[code](src/main/java/org/acme/GreetingResource.java) Notice that the
GREETING_ENV environment variable is used in creating the greeting
message.








