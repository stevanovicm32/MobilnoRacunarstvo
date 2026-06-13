CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    total_points INTEGER NOT NULL DEFAULT 0 CHECK (total_points >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS drops (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    location GEOGRAPHY(Point, 4326) NOT NULL,
    photo_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    active_at TIMESTAMPTZ NOT NULL,
    first_claimer_id UUID REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT drops_active_after_created CHECK (active_at > created_at)
);

CREATE TABLE IF NOT EXISTS claims (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    drop_id UUID NOT NULL REFERENCES drops(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    points_awarded INTEGER NOT NULL CHECK (points_awarded IN (50, 500)),
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (drop_id, user_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS drops_one_per_creator_week_idx
    ON drops (creator_id, date_trunc('week', created_at AT TIME ZONE 'UTC'));

CREATE INDEX IF NOT EXISTS drops_location_gist_idx ON drops USING GIST (location);
CREATE INDEX IF NOT EXISTS drops_active_at_idx ON drops (active_at);
CREATE INDEX IF NOT EXISTS drops_creator_id_idx ON drops (creator_id);
CREATE INDEX IF NOT EXISTS claims_user_id_idx ON claims (user_id);
CREATE INDEX IF NOT EXISTS claims_drop_id_idx ON claims (drop_id);
