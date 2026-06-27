package api

import (
	"errors"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/repository"
)

type DropHandler struct {
	dropRepo repository.DropRepository
	userRepo repository.UserRepository
}

func NewDropHandler(dr repository.DropRepository, ur repository.UserRepository) *DropHandler {
	return &DropHandler{dropRepo: dr, userRepo: ur}
}

func (h *DropHandler) CreateDrop(c *gin.Context) {
	userID := c.MustGet("userID").(uuid.UUID)

	var req struct {
		Latitude    *float64 `json:"latitude"`
		Longitude   *float64 `json:"longitude"`
		PhotoURL    string   `json:"photo_url"`
		Description string   `json:"description"`
		Hint        string   `json:"hint"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid JSON body"})
		return
	}

	if req.Latitude == nil || req.Longitude == nil || strings.TrimSpace(req.PhotoURL) == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "latitude, longitude, and photo_url are required"})
		return
	}
	if !validCoordinate(*req.Latitude, *req.Longitude) {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid latitude or longitude"})
		return
	}

	drop, err := h.dropRepo.CreateDrop(
		c.Request.Context(),
		userID,
		*req.Latitude,
		*req.Longitude,
		strings.TrimSpace(req.PhotoURL),
		strings.TrimSpace(req.Description),
		strings.TrimSpace(req.Hint),
	)
	if err != nil {
		switch {
		case errors.Is(err, repository.ErrWeeklyLimit):
			c.JSON(http.StatusConflict, gin.H{"error": "Weekly limit reached"})
		case errors.Is(err, repository.ErrNearbyDrop):
			c.JSON(http.StatusConflict, gin.H{"error": "Another active drop is within 50 meters"})
		default:
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to create drop"})
		}
		return
	}

	c.JSON(http.StatusCreated, gin.H{"drop": drop})
}

func (h *DropHandler) GetHeatmap(c *gin.Context) {
	bbox, err := parseBoundingBox(c)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	cells, err := h.dropRepo.GetHeatmap(c.Request.Context(), bbox)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to load heatmap"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"cells": cells})
}

func (h *DropHandler) GetNearbyDrops(c *gin.Context) {
	latitude, err := parseRequiredFloat(c, "latitude")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	longitude, err := parseRequiredFloat(c, "longitude")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	if !validCoordinate(latitude, longitude) {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid latitude or longitude"})
		return
	}

	radius := 20.0
	if radiusParam := c.Query("radius"); radiusParam != "" {
		parsed, err := strconv.ParseFloat(radiusParam, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "radius must be a number"})
			return
		}
		radius = parsed
	}
	if radius <= 0 || radius > 50 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "radius must be between 1 and 50"})
		return
	}

	drops, err := h.dropRepo.GetNearbyDrops(c.Request.Context(), latitude, longitude, radius)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to load nearby drops"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"drops": drops})
}

func (h *DropHandler) ClaimDrop(c *gin.Context) {
	userID := c.MustGet("userID").(uuid.UUID)

	dropID, err := uuid.Parse(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid drop ID"})
		return
	}

	var req struct {
		Latitude  *float64 `json:"latitude"`
		Longitude *float64 `json:"longitude"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid JSON body"})
		return
	}
	if req.Latitude == nil || req.Longitude == nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "latitude and longitude are required"})
		return
	}
	if !validCoordinate(*req.Latitude, *req.Longitude) {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid latitude or longitude"})
		return
	}

	claim, err := h.dropRepo.ClaimDrop(c.Request.Context(), userID, dropID, *req.Latitude, *req.Longitude)
	if err != nil {
		switch {
		case errors.Is(err, repository.ErrDropNotFound):
			c.JSON(http.StatusNotFound, gin.H{"error": "Drop not found"})
		case errors.Is(err, repository.ErrDropInactive):
			c.JSON(http.StatusConflict, gin.H{"error": "Drop is not active yet"})
		case errors.Is(err, repository.ErrTooFarFromDrop):
			c.JSON(http.StatusForbidden, gin.H{"error": "You must be within 20 meters of the drop"})
		case errors.Is(err, repository.ErrAlreadyClaimed):
			c.JSON(http.StatusConflict, gin.H{"error": "You have already claimed this drop"})
		default:
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to claim drop"})
		}
		return
	}

	c.JSON(http.StatusOK, gin.H{"claim": claim})
}

func (h *DropHandler) GetLeaderboard(c *gin.Context) {
	lb, err := h.userRepo.GetLeaderboard(10)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to retrieve leaderboard"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"leaderboard": lb})
}

func validCoordinate(latitude, longitude float64) bool {
	return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
}

func parseBoundingBox(c *gin.Context) (repository.BoundingBox, error) {
	minLat, err := parseRequiredFloat(c, "min_lat")
	if err != nil {
		return repository.BoundingBox{}, err
	}
	minLng, err := parseRequiredFloat(c, "min_lng")
	if err != nil {
		return repository.BoundingBox{}, err
	}
	maxLat, err := parseRequiredFloat(c, "max_lat")
	if err != nil {
		return repository.BoundingBox{}, err
	}
	maxLng, err := parseRequiredFloat(c, "max_lng")
	if err != nil {
		return repository.BoundingBox{}, err
	}

	if !validCoordinate(minLat, minLng) || !validCoordinate(maxLat, maxLng) {
		return repository.BoundingBox{}, errors.New("Invalid bounding box coordinates")
	}
	if minLat >= maxLat || minLng >= maxLng {
		return repository.BoundingBox{}, errors.New("Invalid bounding box range")
	}

	return repository.BoundingBox{
		MinLat: minLat,
		MinLng: minLng,
		MaxLat: maxLat,
		MaxLng: maxLng,
	}, nil
}

func parseRequiredFloat(c *gin.Context, name string) (float64, error) {
	value := c.Query(name)
	if value == "" {
		return 0, errors.New(name + " is required")
	}

	parsed, err := strconv.ParseFloat(value, 64)
	if err != nil {
		return 0, errors.New(name + " must be a number")
	}

	return parsed, nil
}
