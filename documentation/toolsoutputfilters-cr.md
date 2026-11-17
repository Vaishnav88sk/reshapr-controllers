# ToolsOutputFilters Custom Resource

## Overview

The `ToolsOutputFilters` Custom Resource (CR) allows you to attach **output filtering rules** to an
existing reShapr [Service](./service-cr.md). Output filters slim down and transform backend tool
responses before they reach LLMs — reducing context overload and token consumption. The control
plane stores the definition as a `RESHAPR_TOOLS_OUTPUT_FILTERS` artifact.

For a given tool, the gateway applies configured steps in order:

1. `jsonRetain` — keep only the specified JSON Pointer branches,
2. `jsonPatches` — apply RFC 6902 JSON Patch operations,
3. `compact` — recursively prune sparse values (`null`, `""`, `[]`, `{}`),
4. `convertToToon` — encode the final JSON output in TOON format.

The `ToolsOutputFilters` CRD uses the `reshapr.io/v1alpha1` API version. The schema is available in
[`toolsoutputfilters.reshapr.io-v1.yml`](../deploy/crd/toolsoutputfilters.reshapr.io-v1.yml).

```yaml
apiVersion: reshapr.io/v1alpha1
kind: ToolsOutputFilters
metadata:
  name: open-meteo-gitops-output-filters
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  service:
    name: open-meteo-api
    version: '1.0'
  filters:
    get_v1_forecast:
      jsonRetain:
        - /current_weather
        - /hourly/temperature_2m
      jsonPatches:
        - op: remove
          path: /hourly_units
      compact: true
      convertToToon: true
```

The instance-targeting annotations (`reshapr.io/instance`, `reshapr.io/organization`) are
mandatory — see the [Instance connection flow](./instance-connection.md). The short name is `tof`.

A complete sample is available in
[`open-meteo-gitops-output-filters.yaml`](../deploy/samples/open-meteo-gitops-output-filters.yaml).

## Status structure

| Field               | Description                                                                             |
|---------------------|-----------------------------------------------------------------------------------------|
| `status.state`      | `UNKNOWN`, `IN_PROGRESS`, `PREEXISTING`, `READY`, or `ERROR`.                           |
| `status.message`    | Human-readable message giving details about the current status.                         |
| `status.serviceId`  | Identifier of the target Service in the reShapr control plane.                          |
| `status.artifactId` | Identifier of the tools output filters artifact within the target Service.              |

## Specification details

| Property       | Description                                                                                            |
|----------------|--------------------------------------------------------------------------------------------------------|
| `service`      | **Mandatory**. Target Service reference (`name` and `version`).                                        |
| `filters`      | **Mandatory**. Map of tool name → filter item (see below).                                             |
| `keepOnDelete` | **Optional**. When `true`, deleting the CR keeps the artifact in the control plane. Defaults to `false`. |

### Filter item

At least one of `jsonRetain`, `jsonPatches`, `compact`, or `convertToToon` should be set.

| Property        | Description                                                                                                          |
|-----------------|----------------------------------------------------------------------------------------------------------------------|
| `jsonRetain`    | **Optional**. List of JSON Pointer paths (starting with `/`) to retain in the output. Everything else is removed.    |
| `jsonPatches`   | **Optional**. List of RFC 6902 JSON Patch operations (`op`, `path`, optional `from` / `value`) to apply to the output. |
| `compact`       | **Optional**. Boolean (or object) enabling recursive removal of sparse values (`null`, `""`, `[]`, `{}`).            |
| `convertToToon` | **Optional**. When `true`, converts the final JSON output to TOON format.                                            |

