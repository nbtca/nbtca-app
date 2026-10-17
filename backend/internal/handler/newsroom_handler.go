package handler

import (
	"errors"
	"log"
	"net/http"
	"net/url"
	"strings"

	"github.com/go-chi/chi/v5"
	"space.nbtca/backend/internal/model"
	"space.nbtca/backend/internal/service"
)

type NewsroomHandler struct {
	svc *service.NewsroomService
}

func NewNewsroomHandler(svc *service.NewsroomService) *NewsroomHandler {
	return &NewsroomHandler{svc: svc}
}

func (h *NewsroomHandler) GetFeed(w http.ResponseWriter, r *http.Request) {
	feed, err := h.svc.GetFeed(r.Context())
	if err != nil {
		log.Printf("newsroom feed unavailable: %v", err)
		feed = model.NewsFeedResponse{
			Featured:   []model.NewsArticle{},
			Recent:     []model.NewsArticle{},
			Categories: []string{},
		}
	}
	writeJSON(w, http.StatusOK, model.ApiResponse{
		Code:    0,
		Message: "success",
		Data:    feed,
	})
}

func (h *NewsroomHandler) GetArticle(w http.ResponseWriter, r *http.Request) {
	rawID := strings.Trim(chi.URLParam(r, "*"), "/")
	id, err := url.PathUnescape(rawID)
	if err != nil {
		id = rawID
	}
	article, err := h.svc.GetArticle(r.Context(), id)
	if err != nil {
		if !errors.Is(err, service.ErrArticleNotFound) {
			log.Printf("newsroom article %q unavailable: %v", id, err)
		}
		writeError(w, http.StatusNotFound, "article not found")
		return
	}
	writeJSON(w, http.StatusOK, model.ApiResponse{
		Code:    0,
		Message: "success",
		Data:    article,
	})
}
