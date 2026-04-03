package model

import (
	"time"

	"github.com/google/uuid"
)

type Drop struct {
	ID                uuid.UUID `gorm:"type:uuid;primaryKey" json:"id"`
	CreatorID         uuid.UUID `json:"creator_id"`
	Latitude          float64   `gorm:"not null" json:"latitude"`
	Longitude         float64   `gorm:"not null" json:"longitude"`
	Description		  string 	`json:"description"`
	Hint              string    `json:"hint"`
	ImageURL          string    `json:"image_url"`
	CreatedAt         time.Time `json:"created_at"`
	ActivationTime    time.Time `json:"activation_time"`
	IsCollectedCount  int       `gorm:"default:0" json:"is_collected_count"`
}
