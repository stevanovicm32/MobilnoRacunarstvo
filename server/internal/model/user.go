package model

import (
	"time"

	"github.com/google/uuid"
)

type User struct {
	ID           uuid.UUID `gorm:"type:uuid;primaryKey" json:"id"`
	Username     string    `gorm:"unique;not null" json:"username"`
	PasswordHash string    `json:"-"`
	TotalPoints  int       `gorm:"default:0" json:"total_points"`
	LastDropAt   time.Time `json:"last_drop_at"`
}
