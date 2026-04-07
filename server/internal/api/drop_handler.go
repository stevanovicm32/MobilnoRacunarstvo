package api

import (
	"fmt"
	"math/rand"
	"net/http"
	"path/filepath"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/model"
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
	val, _ := c.Get("userID")
	uid := val.(uuid.UUID)

	user, err := h.userRepo.GetById(uid)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "User not found"})
		return
	}

	weekLimit := time.Now().AddDate(0, 0, -7)
	if !user.LastDropAt.IsZero() && user.LastDropAt.After(weekLimit) {
		nextAvailable := user.LastDropAt.AddDate(0, 0, 7)
		c.JSON(http.StatusForbidden, gin.H{
			"error":             "Weekly limit reached",
			"next_available_at": nextAvailable,
		})
		return
	}

	file, err := c.FormFile("image")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Image file is required"})
		return
	}

	lat, _ := strconv.ParseFloat(c.PostForm("latitude"), 64)
	lng, _ := strconv.ParseFloat(c.PostForm("longitude"), 64)
	description := c.PostForm("description")
	hint := c.PostForm("hint")

	filename := fmt.Sprintf("%s_%s", uuid.New().String(), file.Filename)
	uploadPath := filepath.Join("uploads", filename)

	if err := c.SaveUploadedFile(file, uploadPath); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to save image"})
		return
	}

	randomDelay := time.Duration(rand.Intn(24)+1) * time.Hour

	drop := &model.Drop{
		ID:               uuid.New(),
		CreatorID:        uid,
		Latitude:         lat,
		Longitude:        lng,
		Description:      description,
		Hint:             hint,
		ImageURL:         "/" + uploadPath,
		CreatedAt:        time.Now(),
		ActivationTime:   time.Now().Add(randomDelay),
		IsCollectedCount: 0,
	}

	if err := h.dropRepo.Create(drop); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to create drop record"})
		return
	}

	user.LastDropAt = time.Now()

	if err := h.userRepo.Update(user); err != nil {
		fmt.Printf("Failed to update users last drop time")
	}

	c.JSON(http.StatusCreated, drop)
}