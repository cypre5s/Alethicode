#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=== Alethicode K3s Cluster Setup ==="
echo ""

if ! command -v k3s &> /dev/null; then
    echo "[1/5] Installing K3s..."
    curl -sfL https://get.k3s.io | sh -
    mkdir -p ~/.kube
    sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
    sudo chown "$(id -u):$(id -g)" ~/.kube/config
    export KUBECONFIG=~/.kube/config
else
    echo "[1/5] K3s already installed, skipping..."
fi

if ! command -v helm &> /dev/null; then
    echo "[2/5] Installing Helm..."
    curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
else
    echo "[2/5] Helm already installed, skipping..."
fi

echo "[3/5] Installing ArgoCD..."
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
echo "  Waiting for ArgoCD to be ready..."
kubectl wait --for=condition=available deployment/argocd-server -n argocd --timeout=300s

echo "[4/5] Deploying Alethicode via Helm..."
helm upgrade --install alethicode "${SCRIPT_DIR}/helm/alethicode" \
    --namespace alethicode \
    --create-namespace \
    --wait \
    --timeout 600s

echo "[5/5] Applying ArgoCD Application..."
kubectl apply -f "${SCRIPT_DIR}/argocd/project.yaml"
kubectl apply -f "${SCRIPT_DIR}/argocd/application.yaml"

echo ""
echo "=== Deployment Complete ==="
echo ""
echo "ArgoCD Admin Password:"
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
echo ""
echo ""
echo "Grafana:     http://localhost:3000  (admin / $(helm get values alethicode -n alethicode -o json 2>/dev/null | python3 -c 'import sys,json; print(json.load(sys.stdin).get("observability",{}).get("grafana",{}).get("adminPassword","alethicode_grafana_2026"))' 2>/dev/null || echo 'alethicode_grafana_2026'))"
echo "Prometheus:  http://localhost:9090"
echo "ArgoCD UI:   http://localhost:8443"
echo ""
echo "Port-forward commands:"
echo "  kubectl port-forward svc/grafana 3000:3000 -n alethicode"
echo "  kubectl port-forward svc/prometheus 9090:9090 -n alethicode"
echo "  kubectl port-forward svc/argocd-server 8443:443 -n argocd"
echo "  kubectl port-forward svc/frontend 8080:80 -n alethicode"
