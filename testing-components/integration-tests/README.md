To run kubernetes tests:

Make sure you have deployed greeting, farewell, and meeting services
The .yml files for that are in their perspective projects

Replace `node.host` with a hostname of cluster (NOT AN IP ADDRESS!!!  Ingress and route tests won't work without a hostname).  On minikube, 
you'll need to add an entry for `minikube ip` into `/etc/hosts`

With minikube:
```shell
mvn -Dno-build-cache -Dnode.host=<host> -Dk8s=true -Dsurefire.skipAfterFailureCount=1 clean package
```

Ingress tests are only run on minikube as I don't know
how ingresses work on openshift

With Openshift
```shell
mvn -Dno-build-cache -Dnode.host=<host> -Dopenshift=true -Dsurefire.skipAfterFailureCount=1 clean package
```