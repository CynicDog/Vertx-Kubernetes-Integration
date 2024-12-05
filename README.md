# Vertx Kubernetes Integration 

The terms `nodes` and `cluster` refer to concepts specific to Vert.x's distributed event bus and are different from their meanings in Kubernetes.

### Comparison of Terminology: Vert.x vs. Kubernetes 

| **Concept**       | **Vert.x**                                                                 | **Kubernetes**                                                    |
|--------------------|---------------------------------------------------------------------------|-------------------------------------------------------------------|
| **Node**           | An instance of a Vert.x application running in a JVM process.            | A physical/virtual machine in a Kubernetes environment.           |
| **Cluster**        | A logical grouping of Vert.x nodes sharing a distributed event bus.      | A group of nodes running and managing containerized applications. |
| **Discovery**      | Handles discovery for message passing across Vert.x nodes.              | Handles service discovery for pods and other cluster resources.   |
| **Communication**  | Event-driven messaging on the event bus.                                | Network-based communication between services and containers.      |
> While Vert.x clusters handle internal messaging and load distribution within Vert.x applications, Kubernetes clusters manage infrastructure-level operations like resource allocation, scaling, and networking for all kinds of applications.

<details><summary><h3>A. Vert.x & Infinispan Cluster Manager on Kind</h3></summary>

### 1. Create a Kind Cluster

```bash
cynicdog@cynicdogui-Mac ~ % kind create --name=vertx 
```

### 2. Apply Kubernetes Resources on the Kind Cluster 

Place the resource files in the `k8s` directory of this project repository on the control plane before running the following command.
```bash
root@vertx-control-plane:/# kubectl apply -f ./k8s/*.yml
```

If pods fail to start with messages like `Vert.x Infinispan getting "failed sending discovery request to /228.6.7.8`, enable multicast with:

```bash
sudo route add -net 224.0.0.0/5 127.0.0.1
```

### 3. Port Forward the Service to Local Machine

Run the command below in a separate terminal to forward the service port from the cluster to your local machine.
```bash
cynicdog@cynicdogui-Mac ~ % kubectl port-forward service/frontend 8080:80 
```

### 4. Test EventBus Communication from Pod to Pod with `/hello` Endpoint on the Frontend Service 
```
cynicdog@cynicdogui-Mac ~ % http :8080/hello name=="Vert.x Clustering"
HTTP/1.1 200 OK
content-length: 64

Hello Vert.x Clustering from backend-deployment-79b4c7864d-m8th5
```

</details>

