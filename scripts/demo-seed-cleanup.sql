-- Opt-in M14 demo-seed cleanup. Do NOT run automatically.
-- Targets only usernames matching demo_% created by scripts/demo-seed.ps1.
-- Does not wipe the database, reset Docker volumes, or delete load12_* / ordinary users.

BEGIN;

CREATE TEMP TABLE demo_users AS
SELECT id
FROM users
WHERE username LIKE 'demo_%';

CREATE TEMP TABLE demo_videos AS
SELECT id
FROM videos
WHERE owner_user_id IN (SELECT id FROM demo_users);

DELETE FROM notifications
WHERE recipient_user_id IN (SELECT id FROM demo_users)
   OR actor_user_id IN (SELECT id FROM demo_users)
   OR video_id IN (SELECT id FROM demo_videos);

DELETE FROM comments
WHERE parent_comment_id IN (
    SELECT id FROM comments
    WHERE author_user_id IN (SELECT id FROM demo_users)
       OR video_id IN (SELECT id FROM demo_videos)
);

DELETE FROM comments
WHERE author_user_id IN (SELECT id FROM demo_users)
   OR video_id IN (SELECT id FROM demo_videos);

DELETE FROM danmaku
WHERE user_id IN (SELECT id FROM demo_users)
   OR video_id IN (SELECT id FROM demo_videos);

DELETE FROM video_likes
WHERE user_id IN (SELECT id FROM demo_users)
   OR video_id IN (SELECT id FROM demo_videos);

DELETE FROM video_favorites
WHERE user_id IN (SELECT id FROM demo_users)
   OR video_id IN (SELECT id FROM demo_videos);

DELETE FROM user_follows
WHERE follower_user_id IN (SELECT id FROM demo_users)
   OR followed_user_id IN (SELECT id FROM demo_users);

DELETE FROM user_video_qualified_views
WHERE user_id IN (SELECT id FROM demo_users)
   OR video_id IN (SELECT id FROM demo_videos);

DELETE FROM video_view_idempotency
WHERE video_id IN (SELECT id FROM demo_videos);

DELETE FROM search_index_outbox
WHERE video_id IN (SELECT id FROM demo_videos);

DELETE FROM upload_sessions
WHERE user_id IN (SELECT id FROM demo_users);

DELETE FROM videos
WHERE id IN (SELECT id FROM demo_videos);

DELETE FROM users
WHERE id IN (SELECT id FROM demo_users);

COMMIT;
