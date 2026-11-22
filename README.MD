# Spool — Provisioning

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/)

**Provisioning** generates the Terraform of the infrastructure of a **Spool** descriptor, for a local target (Docker
and LocalStack) or for AWS. Every piece of the infrastructure asks the plugin of its type for its Terraform, so a new
plugin brings its own. Nothing is applied: the files are written for someone to review and apply.

Decided in [ADR-024](https://linear.app/javier-castilla/document/adr-024-cada-plugin-genera-el-terraform-de-sus-piezas-para-un-destino-e4f6ee200935).

> Requires **Java 21+**.
