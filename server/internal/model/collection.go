package model

import (
	"time"

	"github.com/google/uuid"
)

type Collection struct {
	ID          uuid.UUID `gorm:"type:uuid;primaryKey" json:"id"`
	UserID      uuid.UUID `gorm:"uniqueIndex:idx_user_drop" json:"user_id"`
	DropID      uuid.UUID `gorm:"uniqueIndex:idx_user_drop" json:"drop_id"`
	CollectedAt time.Time `json:"collected_at"`
	PointsEarned int       `json:"points_earned"`
}
