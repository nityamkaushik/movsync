-- 1. Modify existing rooms table
ALTER TABLE rooms ALTER COLUMN movie_fingerprint DROP NOT NULL;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS video_type VARCHAR(20) DEFAULT 'local';
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS video_url TEXT;

-- 2. Create playlists table
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

-- 3. Enable RLS and Policies for playlists
ALTER TABLE playlists ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS playlists_select ON playlists;
DROP POLICY IF EXISTS playlists_insert ON playlists;
DROP POLICY IF EXISTS playlists_update ON playlists;
DROP POLICY IF EXISTS playlists_delete ON playlists;

CREATE POLICY playlists_select ON playlists FOR SELECT USING (true);
CREATE POLICY playlists_insert ON playlists FOR INSERT WITH CHECK (true);
CREATE POLICY playlists_update ON playlists FOR UPDATE USING (true);
CREATE POLICY playlists_delete ON playlists FOR DELETE USING (true);
