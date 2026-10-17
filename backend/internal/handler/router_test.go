package handler

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"space.nbtca/backend/internal/config"
	"space.nbtca/backend/internal/model"
)

func TestRouterEndpoints(t *testing.T) {
	mux := http.NewServeMux()
	var server *httptest.Server
	mux.HandleFunc("/rss.xml", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/rss+xml; charset=utf-8")
		fmt.Fprintf(w, `<rss version="2.0"><channel><item><title>测试文章</title><link>%s/posts/PhoNBT2024</link><description>摘要</description><category>活动</category><category>摄影</category><author>NBTCA</author><pubDate>Wed, 18 Jun 2024 00:00:00 GMT</pubDate></item></channel></rss>`, server.URL)
	})
	mux.HandleFunc("/posts/PhoNBT2024", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprint(w, `<html><head><meta property="og:image" content="/cover.jpg"></head><body><article><h1>测试文章</h1><p>正文内容</p></article></body></html>`)
	})
	mux.HandleFunc("/school.ics", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/calendar")
		fmt.Fprint(w, `BEGIN:VCALENDAR
BEGIN:VEVENT
UID:start
DTSTART;VALUE=DATE:20260914
SUMMARY:秋季学期开始上课
END:VEVENT
BEGIN:VEVENT
UID:holiday
DTSTART;VALUE=DATE:20270101
SUMMARY:元旦放假
END:VEVENT
END:VCALENDAR
`)
	})
	mux.HandleFunc("/v2/ping", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"message":"Hello"}`)
	})
	mux.HandleFunc("/api/status", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"state":"operational","updated":1792238100,"groups":[],"incidents":[]}`)
	})
	mux.HandleFunc("/oidc/me", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	})
	server = httptest.NewServer(mux)
	defer server.Close()

	cfg := &config.Config{
		Port:                "0",
		SpaceSiteEndpoint:   server.URL,
		SaturdayAPIEndpoint: server.URL,
		CalendarEndpoint:    server.URL,
		StatusAPIEndpoint:   server.URL + "/api/status",
		LogtoEndpoint:       server.URL,
		HTTPTimeout:         10 * time.Second,
		CORSAllowedOrigins:  []string{"*"},
		AppLatestVersion:    "1.0.0",
		AppVersionCode:      100,
		AppDownloadURL:      "https://example.invalid/app.apk",
		AppChangelog:        "changes",
		AppReleaseDate:      "2026-10-16",
	}
	router := NewRouter(cfg)

	tests := []struct {
		name       string
		method     string
		path       string
		body       []byte
		wantStatus int
		wantBody   string
	}{
		{name: "health", method: http.MethodGet, path: "/health", wantStatus: http.StatusOK, wantBody: "healthy"},
		{name: "news feed", method: http.MethodGet, path: "/api/v1/newsroom/feed", wantStatus: http.StatusOK, wantBody: "测试文章"},
		{name: "news article", method: http.MethodGet, path: "/api/v1/newsroom/articles/PhoNBT2024", wantStatus: http.StatusOK, wantBody: "content_markdown"},
		{name: "calendar", method: http.MethodGet, path: "/api/v1/tools/calendar", wantStatus: http.StatusOK, wantBody: "total_weeks"},
		{name: "infrastructure", method: http.MethodGet, path: "/api/v1/tools/infrastructure", wantStatus: http.StatusOK, wantBody: "operational"},
		{name: "saturday status", method: http.MethodGet, path: "/api/v1/repair/status", wantStatus: http.StatusOK, wantBody: "Saturday API 连接正常"},
		{name: "version", method: http.MethodGet, path: "/api/v1/tools/version", wantStatus: http.StatusOK, wantBody: "1.0.0"},
		{name: "auth missing token", method: http.MethodGet, path: "/api/v1/auth/profile", wantStatus: http.StatusUnauthorized, wantBody: "unauthorized"},
		{name: "removed mock tickets", method: http.MethodGet, path: "/api/v1/repair/tickets", wantStatus: http.StatusNotFound},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			req := httptest.NewRequest(test.method, test.path, bytes.NewReader(test.body))
			if test.body != nil {
				req.Header.Set("Content-Type", "application/json")
			}
			rec := httptest.NewRecorder()
			router.ServeHTTP(rec, req)
			if rec.Code != test.wantStatus {
				t.Fatalf("status = %d, want %d; body = %s", rec.Code, test.wantStatus, rec.Body.String())
			}
			if test.wantBody != "" && !strings.Contains(rec.Body.String(), test.wantBody) {
				t.Fatalf("body = %s, want substring %q", rec.Body.String(), test.wantBody)
			}
		})
	}
}

func TestDiagnoseRoute(t *testing.T) {
	cfg := &config.Config{HTTPTimeout: 10 * time.Second, CORSAllowedOrigins: []string{"*"}}
	router := NewRouter(cfg)
	payload := []byte(`{"device_type":"laptop","description":"开机突然蓝屏"}`)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/repair/diagnose", bytes.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, body = %s", rec.Code, rec.Body.String())
	}
	var response struct {
		Code int                     `json:"code"`
		Data model.DiagnosisResponse `json:"data"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &response); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if response.Code != 0 || response.Data.Source != "heuristic" || len(response.Data.PossibleCauses) == 0 {
		t.Fatalf("response = %+v", response)
	}
}

func TestNewsFeedUpstreamFailureReturnsEmptyFeed(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "rss down", http.StatusInternalServerError)
	}))
	defer upstream.Close()

	cfg := &config.Config{
		HTTPTimeout:        10 * time.Second,
		SpaceSiteEndpoint:  upstream.URL,
		CORSAllowedOrigins: []string{"*"},
	}
	router := NewRouter(cfg)
	req := httptest.NewRequest(http.MethodGet, "/api/v1/newsroom/feed", nil)
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200; body = %s", rec.Code, rec.Body.String())
	}
	var response struct {
		Code int                    `json:"code"`
		Data model.NewsFeedResponse `json:"data"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &response); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if response.Code != 0 || response.Data.Featured == nil || response.Data.Recent == nil || response.Data.Categories == nil {
		t.Fatalf("response = %+v", response)
	}
	if len(response.Data.Featured) != 0 || len(response.Data.Recent) != 0 || len(response.Data.Categories) != 0 {
		t.Fatalf("expected empty feed, got %+v", response.Data)
	}
}

func TestHierarchicalArticleRoute(t *testing.T) {
	mux := http.NewServeMux()
	var server *httptest.Server
	mux.HandleFunc("/rss.xml", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintf(w, `<rss version="2.0"><channel><item><title>嵌套文章</title><link>%s/posts/blogs/Tech/GO/go-struct-craft-2-disable-compare</link><description>摘要</description><category>技术</category><pubDate>Fri, 03 Apr 2026 00:00:00 GMT</pubDate></item></channel></rss>`, server.URL)
	})
	mux.HandleFunc("/posts/blogs/Tech/GO/go-struct-craft-2-disable-compare", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, `<html><body><article><h1>嵌套文章</h1><p>正文</p></article></body></html>`)
	})
	server = httptest.NewServer(mux)
	defer server.Close()

	cfg := &config.Config{HTTPTimeout: 10 * time.Second, SpaceSiteEndpoint: server.URL, CORSAllowedOrigins: []string{"*"}}
	router := NewRouter(cfg)
	for _, path := range []string{
		"/api/v1/newsroom/articles/blogs/Tech/GO/go-struct-craft-2-disable-compare",
		"/api/v1/newsroom/articles/blogs%2FTech%2FGO%2Fgo-struct-craft-2-disable-compare",
	} {
		req := httptest.NewRequest(http.MethodGet, path, nil)
		rec := httptest.NewRecorder()
		router.ServeHTTP(rec, req)
		if rec.Code != http.StatusOK {
			t.Fatalf("path %s status = %d, body = %s", path, rec.Code, rec.Body.String())
		}
		if !strings.Contains(rec.Body.String(), "content_markdown") {
			t.Fatalf("path %s body = %s", path, rec.Body.String())
		}
	}
}
