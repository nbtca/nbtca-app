package service

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"space.nbtca/backend/internal/config"
	"space.nbtca/backend/internal/model"
)

var ErrMissingToken = errors.New("authorization token is required")
var ErrInvalidToken = errors.New("authorization token is invalid")

// AuthService proxies Logto's userinfo endpoint. The BFF never mints or caches
// tokens; the mobile client's bearer token is forwarded as-is.
type AuthService struct {
	cfg    *config.Config
	client *http.Client
}

func NewAuthService(cfg *config.Config, client *http.Client) *AuthService {
	return &AuthService{cfg: cfg, client: newHTTPClient(cfg, client)}
}

func (s *AuthService) Profile(ctx context.Context, authorization string) (*model.UserProfile, error) {
	authorization = strings.TrimSpace(authorization)
	if authorization == "" || authorization == "Bearer" {
		return nil, ErrMissingToken
	}
	if s.cfg == nil || s.cfg.LogtoEndpoint == "" {
		return nil, errors.New("LOGTO_ENDPOINT is not configured")
	}

	body, err := doRequest(ctx, s.client, http.MethodGet, s.cfg.LogtoEndpoint+"/oidc/me", nil, map[string]string{
		"Authorization": authorization,
		"Accept":        "application/json",
	})
	if err != nil {
		var statusErr *UpstreamStatusError
		if errors.As(err, &statusErr) && (statusErr.StatusCode == http.StatusUnauthorized || statusErr.StatusCode == http.StatusForbidden) {
			return nil, ErrInvalidToken
		}
		return nil, err
	}

	var payload struct {
		Sub      string   `json:"sub"`
		Name     string   `json:"name"`
		Username string   `json:"username"`
		Email    string   `json:"email"`
		Picture  string   `json:"picture"`
		Roles    []string `json:"roles"`
		Role     string   `json:"role"`
	}
	if err := json.Unmarshal(body, &payload); err != nil {
		return nil, err
	}
	if strings.TrimSpace(payload.Sub) == "" {
		return nil, ErrInvalidToken
	}
	roles := payload.Roles
	if len(roles) == 0 && payload.Role != "" {
		roles = []string{payload.Role}
	}
	if roles == nil {
		roles = []string{}
	}
	return &model.UserProfile{
		Sub:      payload.Sub,
		Name:     payload.Name,
		Username: payload.Username,
		Email:    payload.Email,
		Picture:  payload.Picture,
		Roles:    roles,
	}, nil
}
