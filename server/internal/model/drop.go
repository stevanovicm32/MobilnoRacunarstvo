package model

import (
	"time"

	"github.com/google/uuid"
)

type Drop struct {
	ID             uuid.UUID  `json:"id"`
	CreatorID      uuid.UUID  `json:"creator_id"`
	Latitude       float64    `json:"latitude,omitempty"`
	Longitude      float64    `json:"longitude,omitempty"`
	Description    string     `json:"description"`
	Hint           string     `json:"hint"`
	PhotoURL       string     `json:"photo_url"`
	CreatedAt      time.Time  `json:"created_at"`
	ActiveAt       time.Time  `json:"active_at"`
	FirstClaimerID *uuid.UUID `json:"first_claimer_id,omitempty"`
}
