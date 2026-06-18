package repository

import (
	"context"
	"errors"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/model"
)

var (
	ErrWeeklyLimit    = errors.New("weekly drop limit reached")
	ErrNearbyDrop     = errors.New("another active drop is too close")
	ErrDropNotFound   = errors.New("drop not found")
	ErrDropInactive   = errors.New("drop is not active yet")
	ErrTooFarFromDrop = errors.New("too far from drop")
	ErrAlreadyClaimed = errors.New("drop already claimed by user")
)

type BoundingBox struct {
	MinLat float64
	MinLng float64
	MaxLat float64
	MaxLng float64
}

type HeatmapCell struct {
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
	Count     int     `json:"count"`
}

type NearbyDrop struct {
	ID              uuid.UUID `json:"id"`
	Latitude        float64   `json:"latitude"`
	Longitude       float64   `json:"longitude"`
	PhotoURL        string    `json:"photo_url"`
	DistanceMeters  float64   `json:"distance_meters"`
}

type DropRepository interface {
	CreateDrop(ctx context.Context, creatorID uuid.UUID, latitude, longitude float64, photoURL string) (*model.Drop, error)
	GetHeatmap(ctx context.Context, bbox BoundingBox) ([]HeatmapCell, error)
	GetNearbyDrops(ctx context.Context, latitude, longitude float64, radiusMeters float64) ([]NearbyDrop, error)
	ClaimDrop(ctx context.Context, userID, dropID uuid.UUID, latitude, longitude float64) (*model.Claim, error)
}

type postgresDropRepository struct{}

func NewDropRepository() DropRepository {
	return &postgresDropRepository{}
}

func (r *postgresDropRepository) CreateDrop(ctx context.Context, creatorID uuid.UUID, latitude, longitude float64, photoURL string) (*model.Drop, error) {
	var exists bool
	err := DB.QueryRow(
		ctx,
		`SELECT EXISTS (
			SELECT 1
			FROM drops
			WHERE active_at <= now()
			  AND ST_DWithin(
				location,
				ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
				50
			  )
		)`,
		longitude,
		latitude,
	).Scan(&exists)
	if err != nil {
		return nil, err
	}
	if exists {
		return nil, ErrNearbyDrop
	}

	var drop model.Drop
	err = DB.QueryRow(
		ctx,
		`INSERT INTO drops (creator_id, location, photo_url, active_at)
		 VALUES (
			$1,
			ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography,
			$4,
			now() + ((7200 + floor(random() * 36001)) * interval '1 second')
		 )
		 RETURNING id,
		           creator_id,
		           ST_Y(location::geometry) AS latitude,
		           ST_X(location::geometry) AS longitude,
		           photo_url,
		           created_at,
		           active_at,
		           first_claimer_id`,
		creatorID,
		longitude,
		latitude,
		photoURL,
	).Scan(
		&drop.ID,
		&drop.CreatorID,
		&drop.Latitude,
		&drop.Longitude,
		&drop.PhotoURL,
		&drop.CreatedAt,
		&drop.ActiveAt,
		&drop.FirstClaimerID,
	)
	if isUniqueViolation(err, "drops_one_per_creator_week_idx") {
		return nil, ErrWeeklyLimit
	}
	if err != nil {
		return nil, err
	}

	return &drop, nil
}

func (r *postgresDropRepository) GetHeatmap(ctx context.Context, bbox BoundingBox) ([]HeatmapCell, error) {
	rows, err := DB.Query(
		ctx,
		`SELECT floor(ST_Y(location::geometry) * 100) / 100 AS latitude_bucket,
		        floor(ST_X(location::geometry) * 100) / 100 AS longitude_bucket,
		        count(*)::int AS drop_count
		 FROM drops
		 WHERE active_at <= now()
		   AND ST_Intersects(
			location::geometry,
			ST_MakeEnvelope($1, $2, $3, $4, 4326)
		   )
		 GROUP BY latitude_bucket, longitude_bucket
		 ORDER BY drop_count DESC`,
		bbox.MinLng,
		bbox.MinLat,
		bbox.MaxLng,
		bbox.MaxLat,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var cells []HeatmapCell
	for rows.Next() {
		var cell HeatmapCell
		if err := rows.Scan(&cell.Latitude, &cell.Longitude, &cell.Count); err != nil {
			return nil, err
		}
		cells = append(cells, cell)
	}

	return cells, rows.Err()
}

func (r *postgresDropRepository) GetNearbyDrops(ctx context.Context, latitude, longitude float64, radiusMeters float64) ([]NearbyDrop, error) {
	rows, err := DB.Query(
		ctx,
		`SELECT id,
		        ST_Y(location::geometry) AS latitude,
		        ST_X(location::geometry) AS longitude,
		        photo_url,
		        ST_Distance(
		          location,
		          ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
		        ) AS distance_meters
		 FROM drops
		 WHERE active_at <= now()
		   AND ST_DWithin(
		         location,
		         ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
		         $3
		       )
		 ORDER BY distance_meters ASC`,
		longitude,
		latitude,
		radiusMeters,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var drops []NearbyDrop
	for rows.Next() {
		var drop NearbyDrop
		if err := rows.Scan(&drop.ID, &drop.Latitude, &drop.Longitude, &drop.PhotoURL, &drop.DistanceMeters); err != nil {
			return nil, err
		}
		drops = append(drops, drop)
	}

	return drops, rows.Err()
}

func (r *postgresDropRepository) ClaimDrop(ctx context.Context, userID, dropID uuid.UUID, latitude, longitude float64) (*model.Claim, error) {
	tx, err := DB.BeginTx(ctx, pgx.TxOptions{IsoLevel: pgx.ReadCommitted})
	if err != nil {
		return nil, err
	}
	defer tx.Rollback(ctx)

	var isActive bool
	var isWithinRadius bool
	var isFirstClaim bool

	// FOR UPDATE serializes claim attempts for this drop. Only the transaction
	// holding the row lock can observe and set first_claimer_id, so the 500-point
	// award cannot be duplicated during simultaneous requests.
	err = tx.QueryRow(
		ctx,
		`SELECT active_at <= now() AS is_active,
		        ST_DWithin(
		          location,
		          ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography,
		          20
		        ) AS is_within_radius,
		        first_claimer_id IS NULL AS is_first_claim
		 FROM drops
		 WHERE id = $1
		 FOR UPDATE`,
		dropID,
		longitude,
		latitude,
	).Scan(&isActive, &isWithinRadius, &isFirstClaim)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrDropNotFound
	}
	if err != nil {
		return nil, err
	}
	if !isActive {
		return nil, ErrDropInactive
	}
	if !isWithinRadius {
		return nil, ErrTooFarFromDrop
	}

	points := 50
	if isFirstClaim {
		points = 500
	}

	claim := &model.Claim{
		ID:            uuid.New(),
		DropID:        dropID,
		UserID:        userID,
		PointsAwarded: points,
	}

	err = tx.QueryRow(
		ctx,
		`INSERT INTO claims (id, drop_id, user_id, points_awarded)
		 VALUES ($1, $2, $3, $4)
		 RETURNING claimed_at`,
		claim.ID,
		claim.DropID,
		claim.UserID,
		claim.PointsAwarded,
	).Scan(&claim.ClaimedAt)
	if isUniqueViolation(err, "claims_drop_id_user_id_key") {
		return nil, ErrAlreadyClaimed
	}
	if err != nil {
		return nil, err
	}

	if isFirstClaim {
		if _, err := tx.Exec(ctx, `UPDATE drops SET first_claimer_id = $1 WHERE id = $2`, userID, dropID); err != nil {
			return nil, err
		}
	}

	if _, err := tx.Exec(ctx, `UPDATE users SET total_points = total_points + $1 WHERE id = $2`, points, userID); err != nil {
		return nil, err
	}

	if err := tx.Commit(ctx); err != nil {
		return nil, err
	}

	return claim, nil
}

func isUniqueViolation(err error, constraintName string) bool {
	if err == nil {
		return false
	}

	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "23505" && pgErr.ConstraintName == constraintName
}
