package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"space.nbtca/backend/internal/config"
	"space.nbtca/backend/internal/handler"
)

func main() {
	cfg := config.Load()
	router := handler.NewRouter(cfg)

	server := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           router,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	log.Printf("NBTCA Mobile App Backend (BFF) starting on %s", server.Addr)
	log.Printf("Saturday API: %s", cfg.SaturdayAPIEndpoint)
	log.Printf("Newsroom:     %s", cfg.SpaceSiteEndpoint)
	log.Printf("Calendar:     %s", cfg.CalendarEndpoint)
	log.Printf("Status page:  %s", cfg.StatusAPIEndpoint)
	if cfg.DifyAPIEndpoint == "" || cfg.DifyAPIKey == "" {
		log.Printf("Dify:         disabled (DIFY_API_ENDPOINT / DIFY_API_KEY not set)")
	} else {
		log.Printf("Dify:         %s", cfg.DifyAPIEndpoint)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := server.Shutdown(shutdownCtx); err != nil {
			log.Printf("graceful shutdown failed: %v", err)
		}
	}()

	if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatalf("server failed: %v", err)
	}
	log.Printf("server stopped")
}
