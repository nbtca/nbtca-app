package service

import (
	"context"
	"net/http"
	"strings"
	"time"

	"space.nbtca/backend/internal/config"
	"space.nbtca/backend/internal/model"
)

// SaturdayService checks that the configured repair API is reachable. The
// mobile client calls Saturday directly for tickets; this endpoint exists so
// operators can verify SATURDAY_API_ENDPOINT without exposing a proxy.
type SaturdayService struct {
	cfg    *config.Config
	client *http.Client
}

func NewSaturdayService(cfg *config.Config, client *http.Client) *SaturdayService {
	return &SaturdayService{cfg: cfg, client: newHTTPClient(cfg, client)}
}

func (s *SaturdayService) Status(ctx context.Context) model.SaturdayStatus {
	endpoint := ""
	if s.cfg != nil {
		endpoint = strings.TrimRight(s.cfg.SaturdayAPIEndpoint, "/")
	}
	if endpoint == "" {
		return model.SaturdayStatus{Reachable: false, Message: "SATURDAY_API_ENDPOINT is not configured"}
	}
	pingURL := endpoint
	if !strings.HasSuffix(pingURL, "/v2") {
		pingURL += "/v2"
	}
	pingURL += "/ping"

	start := time.Now()
	body, err := doRequest(ctx, s.client, http.MethodGet, pingURL, nil, nil)
	latency := time.Since(start).Milliseconds()
	if err != nil {
		return model.SaturdayStatus{
			Endpoint:  pingURL,
			Reachable: false,
			LatencyMS: latency,
			Message:   "Saturday API 不可达",
		}
	}
	if !strings.Contains(string(body), "Hello") {
		return model.SaturdayStatus{
			Endpoint:  pingURL,
			Reachable: false,
			LatencyMS: latency,
			Message:   "Saturday API 返回了非预期响应",
		}
	}
	return model.SaturdayStatus{
		Endpoint:  pingURL,
		Reachable: true,
		LatencyMS: latency,
		Message:   "Saturday API 连接正常",
	}
}
