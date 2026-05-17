#!/usr/bin/env bash
set -euo pipefail

# Execute from: gw

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

log "monitoring preflight and bootstrap"
run_playbook ops/playbooks/check-monitoring.yml
run_playbook ops/playbooks/bootstrap-monitoring.yml

log "installing node-exporter"
run_playbook ops/playbooks/install-node-exporter.yml

log "installing Prometheus and Grafana"
run_playbook ops/playbooks/install-prometheus.yml
run_playbook ops/playbooks/install-grafana.yml

log "installing database and Kubernetes metrics"
run_playbook ops/playbooks/render-postgres-exporter-env.yml
run_playbook ops/playbooks/install-postgres-exporter.yml
run_playbook ops/playbooks/install-kube-state-metrics.yml

log "reconciling Prometheus and Grafana after metrics additions"
run_playbook ops/playbooks/install-prometheus.yml
run_playbook ops/playbooks/install-grafana.yml

log "checking monitoring stack"
run_playbook ops/playbooks/check-monitoring-stack.yml
