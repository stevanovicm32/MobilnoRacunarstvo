package model

import (
	"time"

	"github.com/google/uuid"
)

type User struct {
	ID           uuid.UUID `json:"id"`
	Username     string    `json:"username"`
	PasswordHash string    `json:"-"`
	TotalPoints  int       `json:"total_points"`
	CreatedAt    time.Time `json:"created_at"`
}
