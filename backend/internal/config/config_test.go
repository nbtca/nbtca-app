package config

import (
	"testing"
	"time"
)

func TestLoadClampsTimeoutAndParsesCORS(t *testing.T) {
	t.Setenv("HTTP_TIMEOUT_SECONDS", "5")
	t.Setenv("CORS_ALLOWED_ORIGINS", " https://nbtca.space, https://repair.nbtca.space , https://nbtca.space ")
	t.Setenv("PORT", "19090")
	t.Setenv("DIFY_API_ENDPOINT", "https://dify.example/v1/")

	cfg := Load()
	if cfg.Port != "19090" {
		t.Errorf("port = %q", cfg.Port)
	}
	if cfg.HTTPTimeout != 8*time.Second {
		t.Errorf("timeout = %v, want 8s", cfg.HTTPTimeout)
	}
	if len(cfg.CORSAllowedOrigins) != 2 {
		t.Errorf("origins = %v", cfg.CORSAllowedOrigins)
	}
	if cfg.DifyAPIEndpoint != "https://dify.example/v1" {
		t.Errorf("dify endpoint = %q", cfg.DifyAPIEndpoint)
	}
}

func TestLoadClampsTimeoutUpperBound(t *testing.T) {
	t.Setenv("HTTP_TIMEOUT_SECONDS", "30")
	cfg := Load()
	if cfg.HTTPTimeout != 12*time.Second {
		t.Errorf("timeout = %v, want 12s", cfg.HTTPTimeout)
	}
}
