package service

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"sync"
	"time"

	"space.nbtca/backend/internal/config"
	"space.nbtca/backend/internal/model"
)

// StatusService proxies the public NBTCA status page. Successful payloads are
// cached for 30 seconds and also retained as a last-good copy so a transient
// status.nbtca.space outage does not empty the app's service dashboard.
type StatusService struct {
	cfg    *config.Config
	client *http.Client

	mu      sync.RWMutex
	data    *model.StatusResponse
	expires time.Time
	hasData bool
	now     func() time.Time
}

func NewStatusService(cfg *config.Config, client *http.Client) *StatusService {
	return &StatusService{
		cfg:    cfg,
		client: newHTTPClient(cfg, client),
		now:    time.Now,
	}
}

func (s *StatusService) Get(ctx context.Context) (*model.StatusResponse, error) {
	now := s.now()
	s.mu.RLock()
	if s.hasData && now.Before(s.expires) {
		data := s.data
		s.mu.RUnlock()
		return data, nil
	}
	lastGood := s.data
	hasLastGood := s.hasData
	s.mu.RUnlock()

	if s.cfg == nil || s.cfg.StatusAPIEndpoint == "" {
		if hasLastGood {
			return lastGood, nil
		}
		return nil, errors.New("STATUS_API_ENDPOINT is not configured")
	}
	body, err := doRequest(ctx, s.client, http.MethodGet, s.cfg.StatusAPIEndpoint, nil, nil)
	if err != nil {
		if hasLastGood {
			return lastGood, nil
		}
		return nil, err
	}
	var status model.StatusResponse
	if err := json.Unmarshal(body, &status); err != nil {
		if hasLastGood {
			return lastGood, nil
		}
		return nil, err
	}
	if status.Groups == nil {
		status.Groups = []model.StatusGroup{}
	}
	if status.Incidents == nil {
		status.Incidents = []json.RawMessage{}
	}

	s.mu.Lock()
	s.data = &status
	s.hasData = true
	s.expires = now.Add(30 * time.Second)
	s.mu.Unlock()
	return &status, nil
}
