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
	username VARCHAR(30) UNIQUE NOT NULL,
	password CHAR(34) NOT NULL, /* MD5-based crypt */
	email VARCHAR(50) UNIQUE NOT NULL,
	reg_date TIMESTAMP NOT NULL,
	has_avatar BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE video (
	video_id SERIAL PRIMARY KEY,
	userid SERIAL NOT NULL, /* video owner */
	name VARCHAR(50) NOT NULL,
	filename VARCHAR(50) NOT NULL,
	description TEXT NOT NULL,
	duration INTEGER NOT NULL CHECK (duration > 0) /* unit: seconds */,
	upload_date TIMESTAMP NOT NULL,

	UNIQUE(userid, name),
	FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE TABLE video_watch (
	watch_id SERIAL PRIMARY KEY,
	video_id SERIAL NOT NULL,
	userid SERIAL NOT NULL,
	watch_date TIMESTAMP NOT NULL,

	FOREIGN KEY (video_id) REFERENCES video(video_id),
	FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE TABLE channel (
	channel_id SERIAL PRIMARY KEY,
	userid SERIAL NOT NULL, /* channel owner */
	name VARCHAR(50) NOT NULL,
	description TEXT NOT NULL,
	creation_date TIMESTAMP NOT NULL,

	FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE TABLE channel_subscription (
	channel_id SERIAL,
	userid SERIAL,

	PRIMARY KEY (channel_id, userid),
	FOREIGN KEY (userid) REFERENCES users(userid),
	FOREIGN KEY (channel_id) REFERENCES channel(channel_id)
);

CREATE TABLE channel_video (
	video_id SERIAL PRIMARY KEY,
	channel_id SERIAL NOT NULL,

	FOREIGN KEY (video_id) REFERENCES video(video_id),
	FOREIGN KEY (channel_id) REFERENCES channel(channel_id)
);

CREATE TABLE comments (
	comment_id SERIAL PRIMARY KEY,
	userid SERIAL NOT NULL,
	video_id SERIAL NOT NULL,
	parent_id SERIAL,
	comment TEXT NOT NULL,
	submit_date TIMESTAMP NOT NULL,

	FOREIGN KEY (video_id) REFERENCES video(video_id),
	FOREIGN KEY (userid) REFERENCES users(userid),
	FOREIGN KEY (parent_id) REFERENCES comments(comment_id)
);

CREATE TYPE SCORE_T AS ENUM('like','dislike');

CREATE TABLE video_score (
	video_id SERIAL NOT NULL,
	userid SERIAL NOT NULL,
	score SCORE_T NOT NULL DEFAULT 'like',

	PRIMARY KEY (video_id, userid),
	FOREIGN KEY (video_id) REFERENCES video(video_id),
	FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE TABLE comment_score (
	comment_id SERIAL,
	userid SERIAL,
	score SCORE_T NOT NULL DEFAULT 'like',

	PRIMARY KEY (comment_id, userid),
	FOREIGN KEY (comment_id) REFERENCES comments(comment_id),
	FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE TABLE playlist (
	list_id SERIAL PRIMARY KEY,
	userid SERIAL NOT NULL,
	name VARCHAR(50) NOT NULL,
	is_public BOOLEAN NOT NULL DEFAULT FALSE,

	UNIQUE(userid, name),
	FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE TABLE playlist_video (
	list_id SERIAL NOT NULL,
	video_id SERIAL NOT NULL,

	PRIMARY KEY (list_id, video_id),
	FOREIGN KEY (list_id) REFERENCES playlist(list_id),
	FOREIGN KEY (video_id) REFERENCES video(video_id)
);

/* triggers [begin] */
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
/* trigers [end] */

COMMIT;
