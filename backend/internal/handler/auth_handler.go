package handler

import (
	"errors"
	"log"
	"net/http"

	"space.nbtca/backend/internal/model"
	"space.nbtca/backend/internal/service"
)

type AuthHandler struct {
	svc *service.AuthService
}

func NewAuthHandler(svc *service.AuthService) *AuthHandler {
	return &AuthHandler{svc: svc}
}

func (h *AuthHandler) GetProfile(w http.ResponseWriter, r *http.Request) {
	profile, err := h.svc.Profile(r.Context(), r.Header.Get("Authorization"))
	if err != nil {
		switch {
		case errors.Is(err, service.ErrMissingToken), errors.Is(err, service.ErrInvalidToken):
			writeError(w, http.StatusUnauthorized, "unauthorized")
		default:
			log.Printf("logto profile unavailable: %v", err)
			writeError(w, http.StatusBadGateway, "身份服务暂时不可用")
		}
		return
	}
	writeJSON(w, http.StatusOK, model.ApiResponse{Code: 0, Message: "success", Data: profile})
}

func (h *AuthHandler) TokenExchange(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusNotImplemented, model.ApiResponse{
		Code:    http.StatusNotImplemented,
		Message: "token exchange is handled by Logto; the BFF does not mint tokens",
	})
}
