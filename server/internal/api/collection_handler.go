package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/repository"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/util"
)

type CollectionHandler struct {
	collRepo repository.CollectionRepository
	dropRepo repository.DropRepository
}

func NewCollectionHandler(cr repository.CollectionRepository, dr repository.DropRepository) *CollectionHandler {
	return &CollectionHandler{collRepo: cr, dropRepo: dr}
}

func (h *CollectionHandler) Collect(c *gin.Context) {
	userID, _ := c.Get("userID")
	uid := userID.(uuid.UUID)
	
	dropIDStr := c.Param("id")
	did, _ := uuid.Parse(dropIDStr)

	var req struct {
		UserLat float64 `json:"lat" binding:"required"`
		UserLng float64 `json:"lon" binding:"required"`
	}
	c.ShouldBindJSON(&req)

	drop, err := h.dropRepo.GetById(did)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Drop not found"})
		return
	}

	dist := util.Distance(req.UserLat, req.UserLng, drop.Latitude, drop.Longitude)
	if dist > 10.0 {
		c.JSON(http.StatusForbidden, gin.H{"error": "Too far away to collect"})
		return
	}

	already, _ := h.collRepo.HasUserCollected(uid, did)
	if already {
		c.JSON(http.StatusConflict, gin.H{"error": "Already collected this drop"})
		return
	}

	points := 10
	if drop.IsCollectedCount == 0 {
		points = 100
	}

	if err := h.collRepo.RecordDiscovery(uid, did, points); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Transaction failed"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"points_earned": points})
}