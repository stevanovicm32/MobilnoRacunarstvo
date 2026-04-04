package repository

import (
	"log"
	"time"

	"github.com/google/uuid"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/model"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/util"
	"gorm.io/gorm"
)

// Interface so implementation can be swapped for testing and/or another DBMS
type DropRepository interface {
	Create(drop *model.Drop) error
	GetActiveNearby(lat, lon float64, radius float64) ([]model.Drop, error)
	GetById(id uuid.UUID) (*model.Drop, error)
	UpdateCount(id uuid.UUID) error
}

type postgresDropRepository struct{}

func NewDropRepository() DropRepository {
	return &postgresDropRepository{}
}

func (r *postgresDropRepository) Create(drop *model.Drop) error {
	return DB.Create(drop).Error
}

func (r *postgresDropRepository) GetActiveNearby(lat, lon float64, radius float64) ([]model.Drop, error) {
	var candidates []model.Drop

	// 0.001 ~ 110m 
	margin := 0.001

	if err := DB.Where("latitude BETWEEN ? AND ?", lat - margin, lat + margin).
	Where("longitude BETWEEN ? AND ?", lon - margin, lon + margin).
	Where("activation_time <= ?", time.Now()).
	Find(&candidates).Error; err != nil {
		log.Println("No candidate drops found.")
		return nil, err
	}
	

	var verifiedNearby []model.Drop

	for _, drop := range candidates {
		dist := util.Distance(lat, lon, drop.Latitude, drop.Longitude)
		if dist <= radius {
			verifiedNearby = append(verifiedNearby, drop)
		}
	}

	return verifiedNearby, nil

}

func (r *postgresDropRepository) GetById(id uuid.UUID) (*model.Drop, error) {
	var drop model.Drop
	err := DB.First(&drop, "id = ?", id).Error
	return &drop, err
}

func (r *postgresDropRepository) UpdateCount(id uuid.UUID) error {
	return DB.Model(&model.Drop{}).Where("id = ?", id).Update("is_collected_count", gorm.Expr("is_collected_count + ?", 1)).Error
}
