package repository

import (
	"github.com/google/uuid"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/model"
)

type UserRepository interface {
	Create(user *model.User) error
	GetByUsername(username string) (*model.User, error)
	GetById(id uuid.UUID) (*model.User, error)
	GetLeaderboard(limit int) ([]model.User, error)
	Update(user *model.User) error
}

type postgresUserRepository struct {}

func NewUserRepository() UserRepository {
	return &postgresUserRepository{}
}

func (r *postgresUserRepository) Create(user *model.User) error {
	return DB.Create(user).Error
}

func (r *postgresUserRepository) GetByUsername(username string) (*model.User, error) {
	var user model.User
	err := DB.First(&user, "username = ?", username).Error
	return &user, err
}

func (r *postgresUserRepository) GetById(id uuid.UUID) (*model.User, error) {
	var user model.User
	err := DB.First(&user, "id = ?", id).Error
	return &user, err
}

func (r *postgresUserRepository) GetLeaderboard(limit int) ([]model.User, error) {
	var users []model.User
	err := DB.First(users).Limit(limit).Error
	return users, err
}

func (r *postgresUserRepository) Update(user *model.User) error {
	return DB.Save(user).Error
}

