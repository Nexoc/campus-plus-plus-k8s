# Ansible Inventories

This directory contains inventory examples.

Active tracked file:

```text
home.example.ini
```

Active local runtime file:

```text
home.local.ini
```

Local inventory files are ignored by git. They may contain real IP addresses,
DNS names, or environment-specific SSH targets.

The active inventory path is home-only.

## Home Lab Setup

Create the local home inventory on `gw`:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
cp ops/inventory/home.example.ini ops/inventory/home.local.ini
```

Then edit only the local file:

```bash
# server: gw
nano ops/inventory/home.local.ini
chmod 600 ops/inventory/home.local.ini
```

Replace placeholders such as:

```text
<gw_ip>
<db_ip>
<dev_ip>
<monitoring_ip>
<s1_ip>
<s2_ip>
<s3_ip>
```

Do not commit `home.local.ini`.

## Required Logical Hosts

Keep these logical hostnames stable:

```text
gw
s4-db
s5-dev
s6-monitoring
s1-prod
s2-prod
s3-prod
```

## Field Meaning

`ansible_host` is the SSH target Ansible uses.

`monitoring_scrape_host` is the address Prometheus on `s6-monitoring` uses to
scrape metrics from that host.

For `gw`, Ansible may use:

```ini
gw ansible_host=127.0.0.1 ansible_connection=local monitoring_scrape_host=<gw_ip>
```

This keeps Ansible local on `gw` while Prometheus still scrapes the reachable
network address.

## Rules

- runtime real addresses stay in `*.local.ini`
- tracked `*.example.ini` files use placeholders only
- Kubernetes manifests and GitHub workflows must not depend on inventory IPs
- stable logical names are the home-lab contract
