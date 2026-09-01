-- Opt-in M13 demo cleanup. Do NOT run automatically.
-- Targets only usernames matching load12_% from the M12 k6 social scenario.
-- Does not wipe the database, reset Docker volumes, or delete unrelated users.
-- Does not change M9 view-count semantics for ordinary videos (including the
-- ~1410-view M12 benchmark fixture on whatever video k6 happened to hit).

BEGIN;

CREATE TEMP TABLE load12_users AS
SELECT id
FROM users
WHERE username LIKE 'load12_%';

CREATE TEMP TABLE load12_videos AS
SELECT id
FROM videos
WHERE owner_user_id IN (SELECT id FROM load12_users);

DELETE FROM notifications
WHERE recipient_user_id IN (SELECT id FROM load12_users)
   OR actor_user_id IN (SELECT id FROM load12_users)
   OR video_id IN (SELECT id FROM load12_videos);

DELETE FROM comments
WHERE parent_comment_id IN (
    SELECT id FROM comments
    WHERE author_user_id IN (SELECT id FROM load12_users)
       OR video_id IN (SELECT id FROM load12_videos)
);

DELETE FROM comments
WHERE author_user_id IN (SELECT id FROM load12_users)
   OR video_id IN (SELECT id FROM load12_videos);

DELETE FROM danmaku
WHERE user_id IN (SELECT id FROM load12_users)
   OR video_id IN (SELECT id FROM load12_videos);

DELETE FROM video_likes
WHERE user_id IN (SELECT id FROM load12_users)
   OR video_id IN (SELECT id FROM load12_videos);

DELETE FROM video_favorites
WHERE user_id IN (SELECT id FROM load12_users)
   OR video_id IN (SELECT id FROM load12_videos);

DELETE FROM user_follows
WHERE follower_user_id IN (SELECT id FROM load12_users)
   OR followed_user_id IN (SELECT id FROM load12_users);

DELETE FROM user_video_qualified_views
WHERE user_id IN (SELECT id FROM load12_users)
   OR video_id IN (SELECT id FROM load12_videos);

DELETE FROM video_view_idempotency
WHERE video_id IN (SELECT id FROM load12_videos);

DELETE FROM search_index_outbox
WHERE video_id IN (SELECT id FROM load12_videos);

DELETE FROM upload_sessions
WHERE user_id IN (SELECT id FROM load12_users);

DELETE FROM videos
WHERE id IN (SELECT id FROM load12_videos);

DELETE FROM users
WHERE id IN (SELECT id FROM load12_users);

COMMIT;
