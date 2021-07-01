START TRANSACTION;

DROP TABLE IF EXISTS video_watch;
DROP TABLE IF EXISTS channel_video;
DROP TABLE IF EXISTS comment_score;
DROP TABLE IF EXISTS video_score;
DROP TABLE IF EXISTS comments;
DROP TABLE IF EXISTS playlist_video;
DROP TABLE IF EXISTS video;
DROP TABLE IF EXISTS channel_subscription;
DROP TABLE IF EXISTS channel;
DROP TABLE IF EXISTS playlist;
DROP TABLE IF EXISTS users;
DROP TYPE IF EXISTS SCORE_T;

CREATE TABLE users (
	userid SERIAL PRIMARY KEY,
	username VARCHAR(30) UNIQUE NOT NULL CHECK(char_length(username) > 3),
	password CHAR(34) NOT NULL, /* MD5-based crypt */
	email VARCHAR(50) UNIQUE NOT NULL,
	reg_date TIMESTAMP NOT NULL,
	has_avatar BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE video (
	video_id SERIAL PRIMARY KEY,
	userid INTEGER NOT NULL, /* video owner */
	name VARCHAR(50) NOT NULL,
	filename VARCHAR(50) NOT NULL,
	description TEXT NOT NULL,
	duration INTEGER NOT NULL CHECK (duration > 0) /* unit: seconds */,
	upload_date TIMESTAMP NOT NULL,

	UNIQUE(userid, name),
	FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE INDEX ON video (name);

CREATE TABLE video_watch (
	watch_id SERIAL PRIMARY KEY,
	video_id INTEGER NOT NULL,
	userid INTEGER NOT NULL,
	watch_date TIMESTAMP NOT NULL,

	FOREIGN KEY (video_id) REFERENCES video(video_id),
	FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE TABLE channel (
	channel_id SERIAL PRIMARY KEY,
	userid INTEGER NOT NULL, /* channel owner */
	name VARCHAR(50) NOT NULL,
	description TEXT NOT NULL,
	creation_date TIMESTAMP NOT NULL,

	FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE INDEX ON channel (name);

CREATE TABLE channel_subscription (
	channel_id INTEGER,
	userid INTEGER,

	PRIMARY KEY (channel_id, userid),
	FOREIGN KEY (userid) REFERENCES users(userid),
	FOREIGN KEY (channel_id) REFERENCES channel(channel_id)
);

/* check video owner = channel owner */
CREATE OR REPLACE FUNCTION is_eq_vid_chnl_owner(vid_id INTEGER, chl_id INTEGER) RETURNS BOOLEAN AS $$
	BEGIN
		RETURN ( SELECT (SELECT userid FROM video WHERE video_id=vid_id)
		IN (SELECT userid FROM channel WHERE channel_id=chl_id) );
	END;
$$ LANGUAGE plpgsql;

CREATE TABLE channel_video (
	video_id INTEGER PRIMARY KEY,
	channel_id INTEGER NOT NULL,

	FOREIGN KEY (video_id) REFERENCES video(video_id) ON DELETE CASCADE,
	FOREIGN KEY (channel_id) REFERENCES channel(channel_id) ON DELETE CASCADE,

	CHECK (is_eq_vid_chnl_owner(video_id, channel_id) = TRUE)
);

CREATE TABLE comments (
	comment_id SERIAL PRIMARY KEY,
	userid INTEGER NOT NULL,
	video_id INTEGER NOT NULL,
	parent_id INTEGER,
	comment TEXT NOT NULL,
	submit_date TIMESTAMP NOT NULL,

	FOREIGN KEY (video_id) REFERENCES video(video_id) ON DELETE CASCADE,
	FOREIGN KEY (userid) REFERENCES users(userid),
	FOREIGN KEY (parent_id) REFERENCES comments(comment_id) ON DELETE SET NULL
);

CREATE TYPE SCORE_T AS ENUM('like','dislike');

CREATE TABLE video_score (
	video_id INTEGER NOT NULL,
	userid INTEGER NOT NULL,
	score SCORE_T NOT NULL DEFAULT 'like',

	PRIMARY KEY (video_id, userid),
	FOREIGN KEY (video_id) REFERENCES video(video_id),
	FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE TABLE comment_score (
	comment_id INTEGER,
	userid INTEGER,
	score SCORE_T NOT NULL DEFAULT 'like',

	PRIMARY KEY (comment_id, userid),
	FOREIGN KEY (comment_id) REFERENCES comments(comment_id),
	FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE TABLE playlist (
	list_id SERIAL PRIMARY KEY,
	userid INTEGER NOT NULL,
	name VARCHAR(50) NOT NULL,
	is_public BOOLEAN NOT NULL DEFAULT FALSE,

	UNIQUE(userid, name),
	FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE INDEX ON playlist (name);

CREATE TABLE playlist_video (
	list_id INTEGER NOT NULL,
	video_id INTEGER NOT NULL,

	PRIMARY KEY (list_id, video_id),
	FOREIGN KEY (list_id) REFERENCES playlist(list_id),
	FOREIGN KEY (video_id) REFERENCES video(video_id)
);

/* triggers [begin] */

/* create default playlist */
CREATE OR REPLACE FUNCTION create_watch_later() RETURNS TRIGGER AS
$$
BEGIN
	INSERT INTO playlist(userid, name)
	VALUES (new.userid, 'watch_later');

	RETURN new;

END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER dflt_plst AFTER INSERT ON users
FOR EACH ROW
EXECUTE PROCEDURE create_watch_later();

/* delete videos before deleting channel */
CREATE OR REPLACE FUNCTION delete_channel_videos() RETURNS TRIGGER AS
$$
BEGIN
	DELETE FROM video WHERE
	video_id IN (
	SELECT video_id FROM channel
	INNER JOIN channel_video USING(channel_id)
	WHERE channel_id = old.channel_id
	);

	RETURN old;

END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER chnl_dlt BEFORE DELETE ON channel
FOR EACH ROW
EXECUTE PROCEDURE delete_channel_videos();

/* trigers [end] */

COMMIT;
