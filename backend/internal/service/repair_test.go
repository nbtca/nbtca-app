package service

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"space.nbtca/backend/internal/config"
	"space.nbtca/backend/internal/model"
)

func TestDiagnoseHeuristicFallback(t *testing.T) {
	svc := NewRepairService(&config.Config{HTTPTimeout: 10 * time.Second}, nil)
	result := svc.Diagnose(context.Background(), model.DiagnosisRequest{
		DeviceType:  "laptop",
		Description: "开机突然蓝屏",
	})
	if result.Source != "heuristic" {
		t.Errorf("source = %q", result.Source)
	}
	if len(result.PossibleCauses) == 0 || !result.SuggestRepair {
		t.Fatalf("unexpected diagnosis: %+v", result)
	}
	if !strings.Contains(strings.Join(result.PossibleCauses, " "), "驱动") {
		t.Errorf("causes = %v", result.PossibleCauses)
	}
}

func TestDiagnoseUsesDifyOutputs(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/workflows/run" {
			t.Errorf("path = %s", r.URL.Path)
		}
		if r.Header.Get("Authorization") != "Bearer test-key" {
			t.Errorf("authorization = %q", r.Header.Get("Authorization"))
		}
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"data":{"status":"succeeded","outputs":{"possible_causes":["Dify 原因一","Dify 原因二"],"self_help_steps":"步骤一\n步骤二","recommended_tools":["工具 A"],"suggest_repair":true,"summary":"Dify 总结"},"error":null}}`)
	}))
	defer server.Close()

	cfg := &config.Config{
		HTTPTimeout:     10 * time.Second,
		DifyAPIEndpoint: server.URL + "/v1",
		DifyAPIKey:      "test-key",
	}
	svc := NewRepairService(cfg, nil)
	result := svc.Diagnose(context.Background(), model.DiagnosisRequest{
		DeviceType:  "laptop",
		Description: "测试",
	})
	if result.Source != "dify" {
		t.Fatalf("source = %q, result = %+v", result.Source, result)
	}
	if len(result.PossibleCauses) != 2 || result.PossibleCauses[0] != "Dify 原因一" {
		t.Errorf("causes = %v", result.PossibleCauses)
	}
	if len(result.SelfHelpSteps) != 2 || result.SelfHelpSteps[1] != "步骤二" {
		t.Errorf("steps = %v", result.SelfHelpSteps)
	}
	if result.Summary != "Dify 总结" || !result.SuggestRepair {
		t.Errorf("result = %+v", result)
	}
}

func TestDiagnoseDifyFailureFallsBack(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "dify down", http.StatusInternalServerError)
	}))
	defer server.Close()

	cfg := &config.Config{
		HTTPTimeout:     10 * time.Second,
		DifyAPIEndpoint: server.URL,
		DifyAPIKey:      "test-key",
	}
	svc := NewRepairService(cfg, nil)
	result := svc.Diagnose(context.Background(), model.DiagnosisRequest{
		DeviceType:  "laptop",
		Description: "开机突然蓝屏",
	})
	if result.Source != "heuristic" {
		t.Fatalf("source = %q", result.Source)
	}
}
