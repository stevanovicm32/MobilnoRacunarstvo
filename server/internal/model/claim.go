package model

import (
	"time"

	"github.com/google/uuid"
)

type Claim struct {
	ID            uuid.UUID `json:"id"`
	DropID        uuid.UUID `json:"drop_id"`
	UserID        uuid.UUID `json:"user_id"`
	PointsAwarded int       `json:"points_awarded"`
	ClaimedAt     time.Time `json:"claimed_at"`
}
