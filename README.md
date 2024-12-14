# Vertx Kubernetes Integration 

### Index 
1. [A. Vert.x & Infinispan Cluster Manager on Kind](#a-vertx--infinispan-cluster-manager-on-kind)
2. [B. Clustered Embedding Server on Kind with Ollama](#b-clustered-embedding-server-on-kind-with-ollama)

## Comparison of Terminology: Vert.x vs. Kubernetes 

The terms `nodes` and `cluster` refer to concepts specific to Vert.x's distributed event bus and are different from their meanings in Kubernetes.

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
cynicdog@cynicdogui-Mac ~ % kind create --name=vertx-infinispan 
```

### 2. Apply Kubernetes Resources on the Kind Cluster 

Place the resource files in the `k8s` directory of this project repository on the control plane before running the following command.
```bash
root@vertx-infinispan-control-plane:/# kubectl apply -f ./k8s/*.yml
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
```bash
cynicdog@cynicdogui-Mac ~ % http :8080/hello name=="Vert.x Clustering"
HTTP/1.1 200 OK
content-length: 64

Hello Vert.x Clustering from backend-deployment-79b4c7864d-m8th5
```

👆 [back to index](#index)

</details>

<details><summary><h3>B. Clustered Embedding Server on Kind with Ollama</h3></summary>

### 1. Create a Kind Cluster

```bash
cynicdog@cynicdogui-Mac ~ % kind create --name=vertx-ollama 
```

### 2. Apply Kubernetes Resources on the Kind Cluster 

Place the resource files in the `k8s` directory of this project repository on the control plane before running the following command.
```bash
root@vertx-ollama-control-plane:/# kubectl apply -f ./k8s/*.yml
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

### 4. Test Embedding and Text Generation Features 

```bash
PS C:\Users> http POST :8080/embed prompt="Llamas are members of the camelid family meaning they're pretty closely related to vicuñas and camels"
HTTP/1.1 200 OK
content-length: 104

Embedding entry stored with key: 451439790
From: backend-deployment-dfb656cc-rtggg (Collection Size: 1)
```

```bash
PS C:\Users> http POST :8080/generate prompt="What animals are llamas related to?"
HTTP/1.1 200 OK
content-length: 1018

Llamas are closely related to vicuñas and camels.

Vicuñas are native to South America and share many traits with llamas, including their warm-blooded physiology, their ability to thrive in a variety of environments (including grasslands, savannas, and tundra), and their unique social structure.

Camels, on the other hand, share some similarities with llamas, such as their similar physical characteristics (e.g., short legs and large horns) that may influence their behavior or social structure.

However, it's important to note that while llamas are closely related to vicuñas and camels in terms of physical similarity, there are also significant differences in terms of culture, language, religion, political power, and other factors that can influence the behavior and social structure of llamas, vicuñas, and camels.

Referenced document: Llamas are members of the camelid family meaning they're pretty closely related to vicuñas and camels.
From: backend-deployment-dfb656cc-rtggg (Collection Size: 1)
```

👆 [back to index](#index)

</details>
