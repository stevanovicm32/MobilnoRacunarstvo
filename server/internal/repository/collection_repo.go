package repository

import (
	"time"

	"github.com/google/uuid"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/model"
	"gorm.io/gorm"
)

type CollectionRepository interface {
	HasUserCollected(userID, dropID uuid.UUID) (bool, error)
	RecordDiscovery(userID, dropID uuid.UUID, points int) error
}

type postgresCollectionRepo struct {
}

func NewCollectionRepository() CollectionRepository {
	return &postgresCollectionRepo{}
}

func (r *postgresCollectionRepo) HasUserCollected(userID, dropID uuid.UUID) (bool, error) {
	var count int64
	err := DB.Model(&model.Collection{}).
		Where("user_id = ? AND drop_id = ?", userID, dropID).
		Count(&count).Error
	return count > 0, err
}

func (r *postgresCollectionRepo) RecordDiscovery(userID, dropID uuid.UUID, points int) error {
	return DB.Transaction(func(tx *gorm.DB) error {
		coll := model.Collection{
			ID:           uuid.New(),
			UserID:       userID,
			DropID:       dropID,
			CollectedAt:  time.Now(),
			PointsEarned: points,
		}
		if err := tx.Create(&coll).Error; err != nil {
			return err
		}

		if err := tx.Model(&model.Drop{}).Where("id = ?", dropID).
			Update("is_collected_count", gorm.Expr("is_collected_count + 1")).Error; err != nil {
			return err
		}

		if err := tx.Model(&model.User{}).Where("id = ?", userID).
			Update("total_points", gorm.Expr("total_points + ?", points)).Error; err != nil {
			return err
		}

		return nil
	})
}

