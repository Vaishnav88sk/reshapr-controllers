# Prompts Custom Resource

## Overview

The `Prompts` Custom Resource (CR) allows you to attach one or more **MCP prompts** to an
existing reShapr [Service](./service-cr.md). A prompt is a reusable, optionally parameterized
message template, stored server-side so that every MCP client (Claude Desktop, Cursor, ...)
gets the same, reviewed wording. Prompts are typically picked by the end user and often tell
the AI which tools to call and how to present the answer.

The `Prompts` CRD is defined using the `reshapr.io/v1alpha1` API version. The full schema
definition is available in
[`prompts.reshapr.io-v1.yml`](../deploy/crd/prompts.reshapr.io-v1.yml).

At a higher level, a `Prompts` resource is organized using the following structure:

```yaml
apiVersion: reshapr.io/v1alpha1
kind: Prompts
metadata:
  name: open-meteo-prompts
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  service:
    name: open-meteo-api
    version: '1.0'
  prompts:
    <prompt-name>:
      <prompt-item>
```

`spec.service` identifies the Service the prompts are attached to (via a name + version pair)
and `spec.prompts` is a map where the key is the prompt name and the value is a
[_Prompt item_](#prompt-item-specification).

The instance-targeting annotations (`reshapr.io/instance`, `reshapr.io/organization`) are
mandatory — see the [Instance connection flow](./instance-connection.md) for details.

Once created in your namespace, you can list existing prompts with:

```sh
$ kubectl get prompts.reshapr.io -n my-ns
NAME                        AGE
open-meteo-gitops-prompts   1d
```

## Status structure

```yaml
status:
  state: READY
  serviceId: 66ca3b482a11675200f87792
  artifactId: 66ca3b482a11675200f87793
  message: Artifact successfully synchronized
```

| Field               | Description                                                                                 |
|---------------------|---------------------------------------------------------------------------------------------|
| `status.state`      | Global reconciliation status: `UNKNOWN`, `IN_PROGRESS`, `PREEXISTING`, `READY`, or `ERROR`. |
| `status.message`    | Human-readable message giving details about the current status.                             |
| `status.serviceId`  | Identifier of the target Service in the reShapr control plane.                              |
| `status.artifactId` | Identifier of the artifact holding the prompts within the target Service.                   |

If the target Service cannot be found in the control plane, the resource is set to `ERROR` and
reconciliation is retried automatically, so applying `Prompts` before the `Service` is safe.

## Prompts specification details

| Property   | Description                                                                              |
|------------|------------------------------------------------------------------------------------------|
| `service`  | **Mandatory**. Reference to the target Service — see _Service reference_ below.          |
| `prompts`  | **Mandatory**. Map of prompt name → [_Prompt item_](#prompt-item-specification).          |

### Service reference (`spec.service`)

| Property   | Description                                                   |
|------------|---------------------------------------------------------------|
| `name`     | **Mandatory**. Human-readable name of the target Service.     |
| `version`  | **Mandatory**. Human-readable version of the target Service.  |

### Prompt item specification

| Property      | Description                                                                                         |
|---------------|-----------------------------------------------------------------------------------------------------|
| `result`      | **Mandatory**. The prompt content. May reference arguments with the `${argument}` notation.         |
| `title`       | **Optional**. Human-readable title of the prompt.                                                   |
| `description` | **Optional**. Human-readable long description of the prompt.                                        |
| `arguments`   | **Optional**. List of [_Prompt arguments_](#prompt-argument) the template accepts.                  |

#### Prompt argument

| Property      | Description                                                      |
|---------------|------------------------------------------------------------------|
| `name`        | **Mandatory**. Name of the argument, as used in `${name}`.        |
| `title`       | **Optional**. Human-readable title of the argument.              |
| `description` | **Optional**. Human-readable long description of the argument.   |
| `required`    | **Optional**. Whether the argument is required. Defaults to `false`. |

## Complete example

```yaml
apiVersion: reshapr.io/v1alpha1
kind: Prompts
metadata:
  name: open-meteo-gitops-prompts
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  service:
    name: open-meteo-api
    version: '1.0'
  prompts:
    travel_advice:
      title: Travel advice
      description: Packing advice for a trip, based on the weather forecast
      arguments:
        - name: city
          title: City
          description: The city you are travelling to
          required: true
      result: |
        Use the weather forecast tool for ${city} for the next 5 days,
        then tell me what to pack in 3 bullet points.
```

A ready-to-apply version is available in
[`open-meteo-gitops-prompts.yaml`](../deploy/samples/open-meteo-gitops-prompts.yaml).
