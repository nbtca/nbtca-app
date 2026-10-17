package service

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
	"time"

	"space.nbtca/backend/internal/config"
)

const userAgent = "nbtca-app-backend/1.0 (+https://nbtca.space)"

const maxResponseBytes = 8 << 20

// UpstreamStatusError exposes the upstream HTTP status so callers can map
// 401/403 responses without parsing error strings.
type UpstreamStatusError struct {
	URL        string
	StatusCode int
	Body       string
}

func (e *UpstreamStatusError) Error() string {
	return fmt.Sprintf("upstream %s returned %d: %s", e.URL, e.StatusCode, e.Body)
}

// newHTTPClient returns the shared client used for every upstream call. The
// timeout is clamped by config.Load; a missing client still gets a safe
// default so services cannot accidentally make unbounded requests.
func newHTTPClient(cfg *config.Config, client *http.Client) *http.Client {
	if client != nil {
		if client.Timeout < 8*time.Second {
			client.Timeout = 10 * time.Second
		}
		return client
	}
	timeout := 10 * time.Second
	if cfg != nil && cfg.HTTPTimeout >= 8*time.Second {
		timeout = cfg.HTTPTimeout
	}
	return &http.Client{Timeout: timeout}
}

// doRequest performs an HTTP request with the required User-Agent and a hard
// read limit. Callers must supply a context with a deadline or use the
// client's timeout.
func doRequest(ctx context.Context, client *http.Client, method, rawURL string, body []byte, headers map[string]string) ([]byte, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	var reader io.Reader
	if body != nil {
		reader = bytes.NewReader(body)
	}
	req, err := http.NewRequestWithContext(ctx, method, rawURL, reader)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", userAgent)
	req.Header.Set("Accept", "application/json, text/html;q=0.9, application/xml;q=0.8, */*;q=0.5")
	for k, v := range headers {
		if v != "" {
			req.Header.Set(k, v)
		}
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	data, err := io.ReadAll(io.LimitReader(resp.Body, maxResponseBytes))
	if err != nil {
		return nil, fmt.Errorf("read %s: %w", rawURL, err)
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		snippet := strings.TrimSpace(string(data))
		if len(snippet) > 180 {
			snippet = snippet[:180]
		}
		return nil, &UpstreamStatusError{URL: rawURL, StatusCode: resp.StatusCode, Body: snippet}
	}
	return data, nil
}

type ttlCache[T any] struct {
	mu      sync.RWMutex
	value   T
	has     bool
	expires time.Time
	ttl     time.Duration
	now     func() time.Time
}

func newTTLCache[T any](ttl time.Duration, now func() time.Time) *ttlCache[T] {
	if now == nil {
		now = time.Now
	}
	return &ttlCache[T]{ttl: ttl, now: now}
}

func (c *ttlCache[T]) get() (T, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	if !c.has || c.now().After(c.expires) {
		var zero T
		return zero, false
	}
	return c.value, true
}

func (c *ttlCache[T]) set(value T) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.value = value
	c.has = true
	c.expires = c.now().Add(c.ttl)
}
