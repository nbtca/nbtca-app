package config

import (
	"os"
	"strconv"
	"strings"
	"time"
)

// Config holds every runtime setting used by the BFF. Endpoints are read
// from the environment so deployments can point the service at test doubles
// without code changes.
type Config struct {
	Port                string
	SaturdayAPIEndpoint string
	SpaceSiteEndpoint   string
	CalendarEndpoint    string
	LogtoEndpoint       string
	StatusAPIEndpoint   string
	DifyAPIEndpoint     string
	DifyAPIKey          string

	HTTPTimeout        time.Duration
	CORSAllowedOrigins []string

	AppLatestVersion string
	AppVersionCode   int
	AppDownloadURL   string
	AppChangelog     string
	AppReleaseDate   string
}

const defaultChangelog = "1. 全新 Newsroom 风格首页资讯聚合；\n2. 电脑义诊与报修工单全流程跟踪，支持 AI 智能预检；\n3. 校历日程、教学周与真实服务状态监控；\n4. 适配深色/浅色模式与 Material You 动态主题。"

// Load reads configuration from the environment, applying the documented
// defaults and clamping the HTTP timeout to the 8-12 second range required by
// the mobile client.
func Load() *Config {
	timeoutSeconds := envInt("HTTP_TIMEOUT_SECONDS", 10)
	if timeoutSeconds < 8 {
		timeoutSeconds = 8
	}
	if timeoutSeconds > 12 {
		timeoutSeconds = 12
	}

	origins := splitCSV(os.Getenv("CORS_ALLOWED_ORIGINS"))
	if len(origins) == 0 {
		origins = []string{"*"}
	}

	return &Config{
		Port:                envString("PORT", "8080"),
		SaturdayAPIEndpoint: strings.TrimRight(envString("SATURDAY_API_ENDPOINT", "https://api.nbtca.space"), "/"),
		SpaceSiteEndpoint:   strings.TrimRight(envString("SPACE_SITE_ENDPOINT", "https://nbtca.space"), "/"),
		CalendarEndpoint:    strings.TrimRight(envString("CALENDAR_ENDPOINT", "https://ical.nbtca.space"), "/"),
		LogtoEndpoint:       strings.TrimRight(envString("LOGTO_ENDPOINT", "https://auth.app.nbtca.space"), "/"),
		StatusAPIEndpoint:   envString("STATUS_API_ENDPOINT", "https://status.nbtca.space/api/status"),
		DifyAPIEndpoint:     strings.TrimRight(strings.TrimSpace(os.Getenv("DIFY_API_ENDPOINT")), "/"),
		DifyAPIKey:          strings.TrimSpace(os.Getenv("DIFY_API_KEY")),
		HTTPTimeout:         time.Duration(timeoutSeconds) * time.Second,
		CORSAllowedOrigins:  origins,

		AppLatestVersion: envString("APP_LATEST_VERSION", "1.0.0"),
		AppVersionCode:   envInt("APP_VERSION_CODE", 100),
		AppDownloadURL:   envString("APP_DOWNLOAD_URL", "https://oss.nbtca.space/app/releases/nbtca-v1.0.0.apk"),
		AppChangelog:     envString("APP_CHANGELOG", defaultChangelog),
		AppReleaseDate:   envString("APP_RELEASE_DATE", "2026-10-16"),
	}
}

func envString(key, fallback string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return fallback
}

func envInt(key string, fallback int) int {
	raw := strings.TrimSpace(os.Getenv(key))
	if raw == "" {
		return fallback
	}
	v, err := strconv.Atoi(raw)
	if err != nil {
		return fallback
	}
	return v
}

func splitCSV(raw string) []string {
	if strings.TrimSpace(raw) == "" {
		return nil
	}
	parts := strings.Split(raw, ",")
	out := make([]string, 0, len(parts))
	seen := make(map[string]struct{}, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p == "" {
			continue
		}
		if _, ok := seen[p]; ok {
			continue
		}
		seen[p] = struct{}{}
		out = append(out, p)
	}
	return out
}
