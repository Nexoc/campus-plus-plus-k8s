# Ansible Inventories

This directory contains inventory examples only.

Tracked files:

```text
lab.example.ini
university.example.ini
```

Local runtime files:

```text
lab.local.ini
university.local.ini
```

Local inventory files are ignored by git. They may contain real IP addresses,
DNS names, or environment-specific SSH targets.

## Lab Setup

Create the local lab inventory on `gw`:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
cp ops/inventory/lab.example.ini ops/inventory/lab.local.ini
```

Then edit only the local file:

```bash
# server: gw
nano ops/inventory/lab.local.ini
```

Replace placeholders such as:

```text
<LAB_GW_IP>
<LAB_DB_IP>
<LAB_DEV_IP>
<LAB_MONITORING_IP>
<LAB_PROD_1_IP>
<LAB_PROD_2_IP>
<LAB_PROD_3_IP>
```

Do not commit `lab.local.ini`.

## Current Lab Example

For the current lab, `lab.local.ini` can be created like this:

This is documentation-only for the current lab. It is not a portable
architecture contract, and the generated `lab.local.ini` file remains ignored
by git.

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
cat > ops/inventory/lab.local.ini <<'EOF'
# Local lab inventory. Do not commit this file.

[gw]
gw ansible_host=127.0.0.1 ansible_connection=local monitoring_scrape_host=192.168.56.10

[db]
s4-db ansible_host=192.168.56.20 monitoring_scrape_host=192.168.56.20

[dev]
s5-dev ansible_host=192.168.56.40 monitoring_scrape_host=192.168.56.40

[monitoring]
s6-monitoring ansible_host=192.168.56.30 monitoring_scrape_host=192.168.56.30

[prod]
s1-prod ansible_host=192.168.56.51 monitoring_scrape_host=192.168.56.51
s2-prod ansible_host=192.168.56.52 monitoring_scrape_host=192.168.56.52
s3-prod ansible_host=192.168.56.53 monitoring_scrape_host=192.168.56.53

[k3s_prod:children]
prod

[all:vars]
ansible_user=nexoc
ansible_ssh_common_args='-o StrictHostKeyChecking=accept-new'
ansible_python_interpreter=/usr/bin/python3
EOF
chmod 600 ops/inventory/lab.local.ini
```

## University Setup

Use the same logical hostnames and groups:

```text
gw
s4-db
s5-dev
s6-monitoring
s1-prod
s2-prod
s3-prod
```

Create a local university inventory from the example:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
cp ops/inventory/university.example.ini ops/inventory/university.local.ini
```

Only the addresses should change. The deployment contract should stay the same.

## Field Meaning

`ansible_host` is the SSH target Ansible uses.

`monitoring_scrape_host` is the address Prometheus on `s6-monitoring` uses to
scrape metrics from that host.

For `gw`, Ansible may use:

```ini
gw ansible_host=127.0.0.1 ansible_connection=local monitoring_scrape_host=<GW_REACHABLE_FROM_MONITORING>
```

This keeps Ansible local on `gw` while Prometheus still scrapes the reachable
network address.

## Rules

- runtime real addresses stay in `*.local.ini`
- the current lab snippet above is documentation-only for recreating the ignored local inventory
- tracked `*.example.ini` files use placeholders only
- Kubernetes manifests and GitHub workflows must not depend on inventory IPs
- stable logical names are the contract across lab and university environments
