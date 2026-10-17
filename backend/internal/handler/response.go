package handler

import (
	"encoding/json"
	"net/http"

	"space.nbtca/backend/internal/model"
)

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, model.ApiResponse{Code: status, Message: message})
}
