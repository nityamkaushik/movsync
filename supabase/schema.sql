CREATE TABLE IF NOT EXISTS rooms (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    code VARCHAR(6) UNIQUE NOT NULL,
    host_id UUID NOT NULL,
    movie_fingerprint VARCHAR(64),
    movie_full_hash VARCHAR(64),
    movie_name TEXT,
    movie_duration_ms BIGINT,
    video_type VARCHAR(20) DEFAULT 'local',
    video_url TEXT,
    status VARCHAR(20) DEFAULT 'waiting',
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS participants (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    room_id UUID REFERENCES rooms(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    is_host BOOLEAN DEFAULT false,
    fingerprint_verified BOOLEAN DEFAULT false,
    full_hash_verified BOOLEAN,
    joined_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(room_id, user_id)
);

CREATE TABLE IF NOT EXISTS playlists (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    room_id UUID REFERENCES rooms(id) ON DELETE CASCADE,
    video_type VARCHAR(20) DEFAULT 'local',
    video_url TEXT NOT NULL,
    title TEXT,
    added_by UUID NOT NULL,
    order_index INTEGER NOT NULL,
    status VARCHAR(20) DEFAULT 'queued',
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE playlists ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS rooms_select ON rooms;
DROP POLICY IF EXISTS rooms_insert ON rooms;
DROP POLICY IF EXISTS rooms_update ON rooms;
DROP POLICY IF EXISTS rooms_delete ON rooms;
DROP POLICY IF EXISTS participants_select ON participants;
DROP POLICY IF EXISTS participants_insert ON participants;
DROP POLICY IF EXISTS participants_update ON participants;
DROP POLICY IF EXISTS participants_delete ON participants;
DROP POLICY IF EXISTS playlists_select ON playlists;
DROP POLICY IF EXISTS playlists_insert ON playlists;
DROP POLICY IF EXISTS playlists_update ON playlists;
DROP POLICY IF EXISTS playlists_delete ON playlists;

CREATE POLICY rooms_select ON rooms FOR SELECT USING (true);
CREATE POLICY rooms_insert ON rooms FOR INSERT WITH CHECK (auth.uid() = host_id);
CREATE POLICY rooms_update ON rooms FOR UPDATE USING (auth.uid() = host_id);
CREATE POLICY rooms_delete ON rooms FOR DELETE USING (auth.uid() = host_id);

CREATE POLICY participants_select ON participants FOR SELECT USING (true);
CREATE POLICY participants_insert ON participants FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY participants_update ON participants FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY participants_delete ON participants FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY playlists_select ON playlists FOR SELECT USING (true);
CREATE POLICY playlists_insert ON playlists FOR INSERT WITH CHECK (true);
CREATE POLICY playlists_update ON playlists FOR UPDATE USING (true);
CREATE POLICY playlists_delete ON playlists FOR DELETE USING (true);

CREATE INDEX IF NOT EXISTS idx_rooms_code ON rooms(code);
CREATE INDEX IF NOT EXISTS idx_participants_room ON participants(room_id);

CREATE OR REPLACE FUNCTION touch_room_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS rooms_touch_updated_at ON rooms;
CREATE TRIGGER rooms_touch_updated_at
    BEFORE UPDATE ON rooms
    FOR EACH ROW
    EXECUTE FUNCTION touch_room_updated_at();

CREATE OR REPLACE FUNCTION expire_stale_rooms(max_age INTERVAL DEFAULT INTERVAL '24 hours')
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM rooms
    WHERE updated_at < now() - max_age
       OR (status = 'ended' AND updated_at < now() - INTERVAL '1 hour');

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
