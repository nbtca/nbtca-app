package handler

import (
	"encoding/json"
	"net/http"
	"strings"

	"space.nbtca/backend/internal/model"
	"space.nbtca/backend/internal/service"
)

type RepairHandler struct {
	svc      *service.RepairService
	saturday *service.SaturdayService
}

func NewRepairHandler(svc *service.RepairService, saturday *service.SaturdayService) *RepairHandler {
	return &RepairHandler{svc: svc, saturday: saturday}
}

func (h *RepairHandler) Status(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, model.ApiResponse{
		Code:    0,
		Message: "success",
		Data:    h.saturday.Status(r.Context()),
	})
}

func (h *RepairHandler) Diagnose(w http.ResponseWriter, r *http.Request) {
	var req model.DiagnosisRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request payload")
		return
	}
	if strings.TrimSpace(req.Description) == "" {
		writeError(w, http.StatusBadRequest, "description is required")
		return
	}
	result := h.svc.Diagnose(r.Context(), req)
	writeJSON(w, http.StatusOK, model.ApiResponse{
		Code:    0,
		Message: "success",
		Data:    result,
	})
}
