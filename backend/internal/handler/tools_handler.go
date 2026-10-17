package handler

import (
	"log"
	"net/http"

	"space.nbtca/backend/internal/config"
	"space.nbtca/backend/internal/model"
	"space.nbtca/backend/internal/service"
)

type ToolsHandler struct {
	cfg      *config.Config
	calendar *service.CalendarService
	status   *service.StatusService
}

func NewToolsHandler(cfg *config.Config, calendar *service.CalendarService, status *service.StatusService) *ToolsHandler {
	return &ToolsHandler{cfg: cfg, calendar: calendar, status: status}
}

func (h *ToolsHandler) GetCalendar(w http.ResponseWriter, r *http.Request) {
	schedule, err := h.calendar.GetSchedule(r.Context())
	if err != nil {
		log.Printf("calendar unavailable: %v", err)
		writeError(w, http.StatusBadGateway, "日历服务暂时不可用")
		return
	}
	writeJSON(w, http.StatusOK, model.ApiResponse{Code: 0, Message: "success", Data: schedule})
}

func (h *ToolsHandler) GetAssociationCalendar(w http.ResponseWriter, r *http.Request) {
	calendar, err := h.calendar.GetAssociationCalendar(r.Context())
	if err != nil {
		log.Printf("association calendar unavailable: %v", err)
		writeError(w, http.StatusBadGateway, "协会日历暂时不可用")
		return
	}
	writeJSON(w, http.StatusOK, model.ApiResponse{Code: 0, Message: "success", Data: calendar})
}

func (h *ToolsHandler) GetInfrastructure(w http.ResponseWriter, r *http.Request) {
	status, err := h.status.Get(r.Context())
	if err != nil {
		log.Printf("status page unavailable: %v", err)
		writeError(w, http.StatusBadGateway, "服务状态数据暂时不可用")
		return
	}
	writeJSON(w, http.StatusOK, model.ApiResponse{Code: 0, Message: "success", Data: status})
}

func (h *ToolsHandler) GetAppVersion(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, model.ApiResponse{
		Code:    0,
		Message: "success",
		Data: model.AppVersionInfo{
			LatestVersion: h.cfg.AppLatestVersion,
			VersionCode:   h.cfg.AppVersionCode,
			DownloadURL:   h.cfg.AppDownloadURL,
			Changelog:     h.cfg.AppChangelog,
			ForceUpdate:   false,
			ReleaseDate:   h.cfg.AppReleaseDate,
		},
	})
}
