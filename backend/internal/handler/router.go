package handler

import (
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	"space.nbtca/backend/internal/config"
	"space.nbtca/backend/internal/model"
	"space.nbtca/backend/internal/service"
)

func NewRouter(cfg *config.Config) http.Handler {
	r := chi.NewRouter()

	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)

	origins := cfg.CORSAllowedOrigins
	if len(origins) == 0 {
		origins = []string{"*"}
	}
	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   origins,
		AllowedMethods:   []string{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type", "X-CSRF-Token"},
		ExposedHeaders:   []string{"Link"},
		AllowCredentials: false,
		MaxAge:           300,
	}))

	client := &http.Client{Timeout: cfg.HTTPTimeout}
	if client.Timeout <= 0 {
		client.Timeout = 10 * time.Second
	}

	newsroomSvc := service.NewNewsroomService(cfg, client)
	repairSvc := service.NewRepairService(cfg, client)
	saturdaySvc := service.NewSaturdayService(cfg, client)
	calendarSvc := service.NewCalendarService(cfg, client)
	statusSvc := service.NewStatusService(cfg, client)
	authSvc := service.NewAuthService(cfg, client)

	newsroomH := NewNewsroomHandler(newsroomSvc)
	repairH := NewRepairHandler(repairSvc, saturdaySvc)
	toolsH := NewToolsHandler(cfg, calendarSvc, statusSvc)
	authH := NewAuthHandler(authSvc)

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, model.ApiResponse{
			Code:    0,
			Message: "NBTCA Backend Service is healthy",
			Data:    healthData(),
		})
	})

	r.Route("/api/v1", func(v1 chi.Router) {
		v1.Route("/newsroom", func(nr chi.Router) {
			nr.Get("/feed", newsroomH.GetFeed)
			nr.Get("/articles/*", newsroomH.GetArticle)
		})

		// Repair tickets are owned by Saturday and are called directly by the
		// mobile client. The BFF only provides AI pre-diagnosis.
		v1.Route("/repair", func(rp chi.Router) {
			rp.Get("/status", repairH.Status)
			rp.Post("/diagnose", repairH.Diagnose)
		})

		v1.Route("/tools", func(tl chi.Router) {
			tl.Get("/calendar", toolsH.GetCalendar)
			tl.Get("/association-calendar", toolsH.GetAssociationCalendar)
			tl.Get("/infrastructure", toolsH.GetInfrastructure)
			tl.Get("/version", toolsH.GetAppVersion)
		})

		v1.Route("/auth", func(at chi.Router) {
			at.Get("/profile", authH.GetProfile)
			at.Post("/token-exchange", authH.TokenExchange)
		})
	})

	return r
}

func healthData() map[string]string {
	return map[string]string{
		"version": "1.0.0",
		"time":    time.Now().Format("2006-01-02T15:04:05Z07:00"),
	}
}
