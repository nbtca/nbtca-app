package service

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"space.nbtca/backend/internal/config"
)

func TestStatusServiceMapsAndCaches(t *testing.T) {
	var hits int32
	var failing atomic.Bool
	mux := http.NewServeMux()
	mux.HandleFunc("/api/status", func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&hits, 1)
		if failing.Load() {
			http.Error(w, "upstream down", http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"state":"major","updated":1792238100,"probes":{"cn":null,"global":1792238040},"groups":[{"name":"Games","infra":false,"components":[{"id":"mc","name":"Minecraft","state":"major","since":1792110960,"failed":["global"],"error":"proxy request failed"}]},{"name":"Platform","infra":true,"components":[{"id":"traefik","name":"Ingress","state":"operational","since":1792224300,"failed":[],"error":null}]}],"incidents":[{"id":"inc-1"}]}`)
	})
	server := httptest.NewServer(mux)
	defer server.Close()

	cfg := testConfig()
	cfg.StatusAPIEndpoint = server.URL + "/api/status"
	svc := NewStatusService(cfg, &http.Client{Timeout: 10 * time.Second})
	fixedNow := time.Date(2026, 10, 17, 12, 0, 0, 0, time.UTC)
	svc.now = func() time.Time { return fixedNow }

	status, err := svc.Get(context.Background())
	if err != nil {
		t.Fatalf("Get: %v", err)
	}
	if status.State != "major" || status.Updated != 1792238100 {
		t.Errorf("status = %+v", status)
	}
	if len(status.Groups) != 2 || status.Groups[0].Components[0].ID != "mc" {
		t.Fatalf("groups = %+v", status.Groups)
	}
	if status.Groups[0].Components[0].Error == nil || *status.Groups[0].Components[0].Error != "proxy request failed" {
		t.Errorf("component error = %v", status.Groups[0].Components[0].Error)
	}
	if !status.Groups[1].Infra {
		t.Errorf("infra group not mapped")
	}
	if len(status.Incidents) != 1 || !json.Valid(status.Incidents[0]) {
		t.Errorf("incidents = %s", status.Incidents)
	}

	if _, err := svc.Get(context.Background()); err != nil {
		t.Fatalf("second Get: %v", err)
	}
	if got := atomic.LoadInt32(&hits); got != 1 {
		t.Errorf("status hits = %d, want 1 (cache)", got)
	}

	failing.Store(true)
	svc.now = func() time.Time { return fixedNow.Add(31 * time.Second) }
	stale, err := svc.Get(context.Background())
	if err != nil {
		t.Fatalf("stale Get: %v", err)
	}
	if stale.State != "major" {
		t.Errorf("stale status = %+v", stale)
	}
	if got := atomic.LoadInt32(&hits); got != 2 {
		t.Errorf("status hits after expiry = %d, want 2", got)
	}
}

func TestStatusServiceNoCacheOnTotalFailure(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "down", http.StatusBadGateway)
	}))
	defer server.Close()

	cfg := &config.Config{HTTPTimeout: 10 * time.Second, StatusAPIEndpoint: server.URL}
	svc := NewStatusService(cfg, nil)
	if _, err := svc.Get(context.Background()); err == nil {
		t.Fatal("expected error when upstream has never succeeded")
	}
}
