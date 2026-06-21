package repository

import (
	"context"
	"time"

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

type postgresUserRepository struct{}

func NewUserRepository() UserRepository {
	return &postgresUserRepository{}
}

func (r *postgresUserRepository) Create(user *model.User) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if user.ID == uuid.Nil {
		user.ID = uuid.New()
	}

	return DB.QueryRow(
		ctx,
		`INSERT INTO users (id, username, password_hash)
		 VALUES ($1, $2, $3)
		 RETURNING created_at, total_points`,
		user.ID,
		user.Username,
		user.PasswordHash,
	).Scan(&user.CreatedAt, &user.TotalPoints)
}

func (r *postgresUserRepository) GetByUsername(username string) (*model.User, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	var user model.User
	err := DB.QueryRow(
		ctx,
		`SELECT id, username, password_hash, total_points, created_at
		 FROM users
		 WHERE username = $1`,
		username,
	).Scan(&user.ID, &user.Username, &user.PasswordHash, &user.TotalPoints, &user.CreatedAt)
	return &user, err
}

func (r *postgresUserRepository) GetById(id uuid.UUID) (*model.User, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	var user model.User
	err := DB.QueryRow(
		ctx,
		`SELECT id, username, password_hash, total_points, created_at
		 FROM users
		 WHERE id = $1`,
		id,
	).Scan(&user.ID, &user.Username, &user.PasswordHash, &user.TotalPoints, &user.CreatedAt)
	return &user, err
}

func (r *postgresUserRepository) GetLeaderboard(limit int) ([]model.User, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if limit <= 0 || limit > 100 {
		limit = 10
	}

	rows, err := DB.Query(
		ctx,
		`SELECT id, username, total_points, created_at
		 FROM users
		 ORDER BY total_points DESC, created_at ASC
		 LIMIT $1`,
		limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	users := make([]model.User, 0)
	for rows.Next() {
		var user model.User
		if err := rows.Scan(&user.ID, &user.Username, &user.TotalPoints, &user.CreatedAt); err != nil {
			return nil, err
		}
		users = append(users, user)
	}

	err = rows.Err()
	return users, err
}

func (r *postgresUserRepository) Update(user *model.User) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	_, err := DB.Exec(
		ctx,
		`UPDATE users
		 SET username = $2, password_hash = $3, total_points = $4
		 WHERE id = $1`,
		user.ID,
		user.Username,
		user.PasswordHash,
		user.TotalPoints,
	)
	return err
}
