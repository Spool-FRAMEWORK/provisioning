# Spool — Provisioning

[![License](https://img.shields.io/badge/License-AGPL%203.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/)

**Provisioning** generates the Terraform of the infrastructure of a **Spool** descriptor, for a local target (Docker
and LocalStack) or for AWS. The generator knows no technology: every piece of the infrastructure asks the plugin of
its type for its Terraform, so a new plugin brings its own. Nothing is applied: the files are written for someone to
review and apply.

Decided in [ADR-024](https://linear.app/javier-castilla/document/adr-024-cada-plugin-genera-el-terraform-de-sus-piezas-para-un-destino-e4f6ee200935).

> Requires **Java 21+**. Applying what it writes requires [Terraform](https://developer.hashicorp.com/terraform/install)
> and, for the local target, Docker.

---

## Generate

```java
GeneratedTerraform terraform = TerraformGenerator.fromFileSystem()
        .generate(Path.of("Spool.yaml"), Optional.of("local"), ProvisioningTarget.LOCAL);
terraform.writeTo(Path.of("infrastructure"));
```

The descriptor is loaded like a node loads it, with its includes and the given profile. It writes `main.tf.json` and,
when there is something to declare, `variables.tf.json`, in Terraform's JSON syntax.

```bash
cd infrastructure
terraform init
terraform apply
```

---

## A complete example

```yaml
infrastructure:
  bus: {kind: EVENT_BUS, type: KAFKA, configuration: {bootstrap.servers: "localhost:9092"}}
  inbox: {kind: INBOX, type: S3, configuration: {bucket: spool-inbox, region: eu-west-1, endpoint: "http://localhost:4566"}}
  lake: {kind: DATA_LAKE, type: S3, configuration: {bucket: "${env:SPOOL_LAKE_BUCKET}", region: eu-west-1, endpoint: "http://localhost:4566"}}
modules:
  - crawler:
      type: POLL
      id: orders-crawler
      source: {type: HTTP, normalizer: JSON_ARRAY, mediaType: JSON, configuration: {sourceId: orders, url: "https://api.example.com/orders"}}
      eventMapping: {namingConvention: SNAKE_CASE}
  - ingester: {type: REACTIVE, id: orders-ingester}
```

| Target | What the Terraform creates |
|---|---|
| `LOCAL` | A Kafka container on port 9092, a LocalStack container on port 4566 and the buckets `spool-inbox` and `${var.SPOOL_LAKE_BUCKET}` in it |
| `AWS` | Fails: a `KAFKA` bus cannot be generated for AWS yet. With an `IN_MEMORY` bus, the two buckets, encrypted and closed to the public, and a role per module: `spool-orders-crawler` reaches the inbox, `spool-orders-ingester` the inbox and the data lake |

What each type gives is in the catalogue of adapters of `spool-infrastructure`.

---

## Rules

- **The plugins decide.** A piece of a type with no `ProvisioningProvider`, or whose provider cannot generate it for
  the target, stops the generation naming the piece and the type: `infrastructure.lake: type FILE_SYSTEM cannot be
  generated for the target aws`.
- **Shared blocks are written once.** Every S3 piece needs the LocalStack container: it appears once. Two pieces that
  need the same block differently, like the `aws` provider with two regions, stop the generation naming both.
- **No value of the environment reaches the files.** Every `${env:NAME}` of the descriptor becomes the Terraform
  variable `NAME`, declared in `variables.tf.json`, to give with `-var` or `TF_VAR_NAME`. A default written as
  `${env:NAME:-default}` is not kept. Credentials are the provider's own (`AWS_PROFILE`...), never the descriptor's.
- **A role per module in AWS.** Each module reaches only the pieces of the kinds it uses, as it resolves them: the
  ones in its `uses`, or the only one of each kind. The service that assumes the roles is the variable
  `module_role_service`, `ecs-tasks.amazonaws.com` by default.
- **Container names and images are variables.** `localstack_container_name`, `kafka_container_name`,
  `localstack_image` and `kafka_image` have defaults, so a machine that already runs a `spool-localstack` can apply
  next to it.
- **The state is local** by default, in the folder where Terraform runs. A remote backend is for whoever deploys to set.

---

## Tests

The scenarios use plugins that exist only in the tests. `TerraformValidateTest` and `TerraformApplyTest` run Terraform
itself (tag `terraform`; the apply also needs Docker, tag `docker`): they look for it in `TERRAFORM_BINARY` or on the
path, and without it they are skipped. The apply uses containers of its own (`spool-tf-*`, ports 4576 and 9192) and
destroys them at the end.
