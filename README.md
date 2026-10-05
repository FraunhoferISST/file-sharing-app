# File Sharing App

This repository provides a generic file sharing application that integrates with the
[Siglet](https://github.com/eclipse-dataplane-core/dsdk-facet-rs/blob/main/docs/siglet.md) to act as a data plane
within a dataspace. The application allows users to manage their own files with metadata, which are stored in MongoDB,
and can transfer those files as part of transfers orchestrated via a control plane.

Therefore, the application exposes two APIs:
- `File API`: for managing files and their metadata, including upload, download, and deletion
- `File Sharing API`: for transferring files as part of a control-plane-orchestrated transfer

The application is designed in a generic way and serves as a reference for use-case specific applications.

# Prerequisites

- Java 25
- Docker
- kubectl

# Build & Run

The application is designed to run within a Kubernetes cluster and in combination with both a Keycloak and a Siglet
instance. While the application itself can generally also be run locally, this requires at least a Keycloak instance
to be able to the use the file API locally. Therefore, the following instructions focus on building and running the
application in a Kubernetes cluster.

## Build Docker Image

To build the Docker image for the application, run the following command:

```shell
docker build -t file-sharing-app:<tag> .
```

## Deployment

The prepared Kubernetes manifests are targeted at running the application as part of
[JAD](https://github.com/eclipse-dataspace-hub/jad). They therefore expect the following prerequisites to be met in
the target cluster:

- an existing `edc-v` namespace
- a Gateway API installation with a gateway named `edcv-gateway` in that namespace
- a running Keycloak instance (only required at runtime)
- a running Siglet instance (only required at runtime)

If you want to deploy the application outside of JAD, make sure these prerequisites are met, or adapt the
manifests accordingly for, e.g. the namespace.

If you are running a cluster locally using e.g. `KinD` or `k3d`, build the application's Docker image and load it into
your local cluster before applying the manifests.

All manifests reside inside the `k8s/` directory. When deployed, the manifests will deploy a MongoDB instance and
the application itself. To deploy the application, run the following command:

```shell
kubectl apply -k k8s/
```

After deployment, the application will be available under `http://file-sharing-app.localhost`.

## Debugging

The application's `deployment.yaml` defines a debug port on 5005. To enable remote debugging, forward the port to
your local machine before connecting your debugger:

```shell
kubectl port-forward deployment/file-sharing-app <local-port>:5005
```

# Usage

As the file sharing API is intended for control-plane-orchestrated transfers, as a user, you will only interact with
file API, which is described in the following.

## API

The file API is rooted at `/api/files/{participantContextId}` and provides the following endpoints:

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/files/{participantContextId}` | Upload a multipart field named `file`; optionally include a `metadata` form field containing JSON. Returns the stored file metadata, including its `id`. |
| `GET` | `/api/files/{participantContextId}` | List metadata for files owned by this participant context. |
| `GET` | `/api/files/{participantContextId}/{id}/metadata` | Get metadata for a file. |
| `GET` | `/api/files/{participantContextId}/{id}` | Download a file. Add `?disposition=inline` to request inline display instead of attachment download. |
| `DELETE` | `/api/files/{participantContextId}/{id}` | Delete the file and its metadata. |

When uploading a file via the API, the complete file metadata will be returned, including the generated `id` of the
stored file. Use this ID on all endpoints that require a file ID.

> Note, that files and metadata are scoped to a specific participant context; any other participant context will not
> be able to access the files. The participant context ID for a request is obtained from both the request path and the
> Keycloak access token. If the two values do not match, the request will be rejected.

## Authorization

The file API is protected by Keycloak and expects a valid token for every request. You must therefore provide one,
containing the participant context ID claim as configured in `application.yml`, in the `Authorization` header of your
requests.

Example request for uploading a file with metadata:

```shell
curl -X POST "http://file-sharing-app.localhost/api/files/participant-123" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./example.txt" \
  -F 'metadata={"category":"example"}'
```
