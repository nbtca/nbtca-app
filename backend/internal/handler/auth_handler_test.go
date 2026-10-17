package handler

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"space.nbtca/backend/internal/config"
	"space.nbtca/backend/internal/model"
	"space.nbtca/backend/internal/service"
)

func TestAuthProfileMissingToken(t *testing.T) {
	cfg := &config.Config{HTTPTimeout: 10 * time.Second, LogtoEndpoint: "http://example.invalid"}
	h := NewAuthHandler(service.NewAuthService(cfg, nil))

	req := httptest.NewRequest(http.MethodGet, "/api/v1/auth/profile", nil)
	rec := httptest.NewRecorder()
	h.GetProfile(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want 401", rec.Code)
	}
	var response model.ApiResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &response); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if response.Code != http.StatusUnauthorized {
		t.Errorf("code = %d", response.Code)
	}
}

func TestAuthProfileProxiesLogto(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/oidc/me" {
			t.Errorf("path = %s", r.URL.Path)
		}
		if r.Header.Get("Authorization") != "Bearer good-token" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"sub":"user-1","name":"宁理同学","username":"geek","email":"geek@nbt.edu.cn","picture":"https://oss.nbtca.space/a.png","roles":["member","Repair Member"]}`)
	}))
	defer upstream.Close()

	cfg := &config.Config{HTTPTimeout: 10 * time.Second, LogtoEndpoint: upstream.URL}
	h := NewAuthHandler(service.NewAuthService(cfg, nil))

	req := httptest.NewRequest(http.MethodGet, "/api/v1/auth/profile", nil)
	req.Header.Set("Authorization", "Bearer good-token")
	rec := httptest.NewRecorder()
	h.GetProfile(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, body = %s", rec.Code, rec.Body.String())
	}
	var response struct {
		Code int               `json:"code"`
		Data model.UserProfile `json:"data"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &response); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if response.Data.Sub != "user-1" || response.Data.Username != "geek" || len(response.Data.Roles) != 2 {
		t.Fatalf("profile = %+v", response.Data)
	}
}

func TestAuthTokenExchangeIsNotImplemented(t *testing.T) {
	h := NewAuthHandler(service.NewAuthService(&config.Config{HTTPTimeout: 10 * time.Second}, nil))
	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/token-exchange", nil)
	rec := httptest.NewRecorder()
	h.TokenExchange(rec, req)
	if rec.Code != http.StatusNotImplemented {
		t.Fatalf("status = %d, want 501", rec.Code)
	}
}
