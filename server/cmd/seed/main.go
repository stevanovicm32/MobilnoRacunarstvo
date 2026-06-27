package main

import (
	"context"
	"fmt"
	"log"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/repository"
	"golang.org/x/crypto/bcrypt"
)

const seedPassword = "password123"

type seedUser struct {
	Username    string
	TotalPoints int
}

type seedDrop struct {
	CreatorUsername string
	Latitude        float64
	Longitude       float64
	Description     string
	Hint            string
	PhotoURL        string
	Active          bool
}

type seedClaim struct {
	DropIndex      int
	Username       string
	PointsAwarded  int
	IsFirstClaimer bool
}

func main() {
	repository.InitDB()
	defer repository.DB.Close()

	ctx := context.Background()
	if err := runSeed(ctx); err != nil {
		log.Fatalf("seed failed: %v", err)
	}

	log.Println("Seed data created successfully.")
	log.Printf("Mock users password: %q", seedPassword)
}

func runSeed(ctx context.Context) error {
	passwordHash, err := bcrypt.GenerateFromPassword([]byte(seedPassword), bcrypt.DefaultCost)
	if err != nil {
		return fmt.Errorf("hash password: %w", err)
	}

	tx, err := repository.DB.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	if _, err := tx.Exec(ctx, `DELETE FROM users WHERE username LIKE 'seed_%'`); err != nil {
		return fmt.Errorf("clear previous seed users: %w", err)
	}

	users := []seedUser{
		{Username: "seed_ana", TotalPoints: 500},
		{Username: "seed_marko", TotalPoints: 500},
		{Username: "seed_jelena", TotalPoints: 50},
		{Username: "seed_nikola", TotalPoints: 500},
		{Username: "seed_milica", TotalPoints: 0},
		{Username: "seed_stefan", TotalPoints: 0},
	}

	userIDs := make(map[string]uuid.UUID, len(users))
	for _, user := range users {
		id := uuid.New()
		_, err := tx.Exec(
			ctx,
			`INSERT INTO users (id, username, password_hash, total_points)
			 VALUES ($1, $2, $3, $4)`,
			id,
			user.Username,
			string(passwordHash),
			user.TotalPoints,
		)
		if err != nil {
			return fmt.Errorf("insert user %q: %w", user.Username, err)
		}
		userIDs[user.Username] = id
	}

	now := time.Now()
	drops := []seedDrop{
		{
			CreatorUsername: "seed_milica",
			Latitude:        44.8235,
			Longitude:       20.4500,
			Description:     "Fortress view drop",
			Hint:            "Near the upper fortress gate overlooking the rivers.",
			PhotoURL:        "/uploads/seed_kalemegdan.jpg",
			Active:          true,
		},
		{
			CreatorUsername: "seed_stefan",
			Latitude:        44.8167,
			Longitude:       20.4608,
			Description:     "Republic Square sticker spot",
			Hint:            "Look for the horse statue in the center.",
			PhotoURL:        "/uploads/seed_republic.jpg",
			Active:          true,
		},
		{
			CreatorUsername: "seed_ana",
			Latitude:        44.8044,
			Longitude:       20.4654,
			Description:     "Slavija fountain drop",
			Hint:            "Close to the big fountain circle.",
			PhotoURL:        "/uploads/seed_slavija.jpg",
			Active:          true,
		},
		{
			CreatorUsername: "seed_marko",
			Latitude:        44.7850,
			Longitude:       20.4100,
			Description:     "Ada Ciganlija lakeside",
			Hint:            "By the main beach path on Ada.",
			PhotoURL:        "/uploads/seed_ada.jpg",
			Active:          true,
		},
		{
			CreatorUsername: "seed_jelena",
			Latitude:        44.8455,
			Longitude:       20.4014,
			Description:     "Zemun quay drop",
			Hint:            "Walk along the Danube promenade in Zemun.",
			PhotoURL:        "/uploads/seed_zemun.jpg",
			Active:          true,
		},
		{
			CreatorUsername: "seed_nikola",
			Latitude:        44.8340,
			Longitude:       20.4720,
			Description:     "Dorćol hidden alley",
			Hint:            "A narrow street with colorful murals.",
			PhotoURL:        "/uploads/seed_dorcol.jpg",
			Active:          true,
		},
		{
			CreatorUsername: "seed_milica",
			Latitude:        44.7985,
			Longitude:       20.4885,
			Description:     "Future Vračar drop",
			Hint:            "This one is not active yet.",
			PhotoURL:        "/uploads/seed_vracar.jpg",
			Active:          false,
		},
	}

	dropIDs := make([]uuid.UUID, 0, len(drops))
	for i, drop := range drops {
		creatorID, ok := userIDs[drop.CreatorUsername]
		if !ok {
			return fmt.Errorf("unknown creator %q", drop.CreatorUsername)
		}

		activeAt := now.Add(-2 * time.Hour)
		createdAt := now.Add(-3 * time.Hour)
		if !drop.Active {
			activeAt = now.Add(6 * time.Hour)
			createdAt = now.Add(-1 * time.Hour)
		}

		// Spread creators across weeks so weekly-limit seeding stays valid.
		createdAt = createdAt.Add(-time.Duration(i*8) * 24 * time.Hour)

		id := uuid.New()
		_, err := tx.Exec(
			ctx,
			`INSERT INTO drops (
				id, creator_id, location, photo_url, description, hint, created_at, active_at
			) VALUES (
				$1, $2,
				ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography,
				$5, $6, $7, $8, $9
			)`,
			id,
			creatorID,
			drop.Longitude,
			drop.Latitude,
			drop.PhotoURL,
			drop.Description,
			drop.Hint,
			createdAt,
			activeAt,
		)
		if err != nil {
			return fmt.Errorf("insert drop %q: %w", drop.Description, err)
		}
		dropIDs = append(dropIDs, id)
	}

	claims := []seedClaim{
		{DropIndex: 0, Username: "seed_marko", PointsAwarded: 500, IsFirstClaimer: true},
		{DropIndex: 0, Username: "seed_jelena", PointsAwarded: 50, IsFirstClaimer: false},
		{DropIndex: 1, Username: "seed_ana", PointsAwarded: 500, IsFirstClaimer: true},
		{DropIndex: 2, Username: "seed_nikola", PointsAwarded: 500, IsFirstClaimer: true},
	}

	for _, claim := range claims {
		dropID := dropIDs[claim.DropIndex]
		userID := userIDs[claim.Username]

		_, err := tx.Exec(
			ctx,
			`INSERT INTO claims (id, drop_id, user_id, points_awarded, claimed_at)
			 VALUES ($1, $2, $3, $4, $5)`,
			uuid.New(),
			dropID,
			userID,
			claim.PointsAwarded,
			now.Add(-30*time.Minute),
		)
		if err != nil {
			return fmt.Errorf("insert claim for %q: %w", claim.Username, err)
		}

		if claim.IsFirstClaimer {
			_, err := tx.Exec(
				ctx,
				`UPDATE drops SET first_claimer_id = $1 WHERE id = $2`,
				userID,
				dropID,
			)
			if err != nil {
				return fmt.Errorf("set first claimer: %w", err)
			}
		}
	}

	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("commit seed transaction: %w", err)
	}

	logSeedSummary(ctx, userIDs)
	return nil
}

func logSeedSummary(ctx context.Context, userIDs map[string]uuid.UUID) {
	var userCount int
	if err := repository.DB.QueryRow(ctx, `SELECT count(*) FROM users WHERE username LIKE 'seed_%'`).Scan(&userCount); err == nil {
		log.Printf("Seeded users: %d", userCount)
	}

	var dropCount int
	if err := repository.DB.QueryRow(
		ctx,
		`SELECT count(*)
		 FROM drops d
		 JOIN users u ON u.id = d.creator_id
		 WHERE u.username LIKE 'seed_%'`,
	).Scan(&dropCount); err == nil {
		log.Printf("Seeded drops around Belgrade: %d", dropCount)
	}

	var activeDropCount int
	if err := repository.DB.QueryRow(
		ctx,
		`SELECT count(*)
		 FROM drops d
		 JOIN users u ON u.id = d.creator_id
		 WHERE u.username LIKE 'seed_%' AND d.active_at <= now()`,
	).Scan(&activeDropCount); err == nil {
		log.Printf("Active drops visible on map: %d", activeDropCount)
	}

	rows, err := repository.DB.Query(
		ctx,
		`SELECT username, total_points
		 FROM users
		 WHERE username LIKE 'seed_%'
		 ORDER BY total_points DESC`,
	)
	if err != nil {
		return
	}
	defer rows.Close()

	for rows.Next() {
		var username string
		var points int
		if err := rows.Scan(&username, &points); err != nil {
			continue
		}
		log.Printf("  %s — %d pts (login: %s / %s)", username, points, username, seedPassword)
	}

	if err := rows.Err(); err != nil && err != pgx.ErrNoRows {
		log.Printf("seed summary query error: %v", err)
	}
}
