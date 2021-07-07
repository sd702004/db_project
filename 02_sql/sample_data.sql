START TRANSACTION;

INSERT INTO users(userid, username, password, email, reg_date)
VALUES
(1, 'saeed', crypt('123456', gen_salt('md5')), 'saeed.haddadian@gmail.com', now()),
(2, 'reza', crypt('123456', gen_salt('md5')), 'reza4018@gmail.com', now()),
(3, 'hamed', crypt('123456', gen_salt('md5')), 'hamed67@gmail.com', now()),
(4, 'pooya', crypt('123456', gen_salt('md5')), 'pooya70@gmail.com', now());

INSERT INTO video(video_id, userid, name, filename, description, duration, upload_date, total_watch)
VALUES
(1, 1, 'OpenGL Course - Create 3D and 2D Graphics With C++', 'opengl.mp4', 'Learn how to use OpenGL to create 2D and 3D vector graphics in this course.', 60, now(), 5),
(2, 1, 'How do games render their scenes?', 'game_render.mp4', 'I am a programmer who works on games, web and VR/AR applications. With my videos I like to share the wonderful world of programming with everyone!', 20, now(), 22),
(3, 1, 'The Future of Cities', 'cities.mp4', 'This is a conversation starter first, a video second. Id love to hear your thoughts on cities, the future, and this project.', 150, now(), 4),
(4, 2, 'Scientist Leaves Everyone Speechless', 'scientist.mp4', 'Full Segment: Britains Got Talent 2020 Auditions | Season 14 E2 Audition: Kevin Quantum', 80, now(), 8),
(5, 2, 'Soul Of The City', 'soul_city.mp4', 'Soul Of The City short film', 87, now(), 15),
(6, 3, 'Nepal', 'nepal', 'I have been fascinated by Nepal since I was a kid. I was transfixed by the highest mountain in the world, Everest, but in time learned of what else the country had to offer. This fall I was I able to see it for myself.', 41, now(), 5),
(7, 4, 'Avatar: Frontiers of Pandora', 'avatar.mp4', 'Coming in 2022 on PlayStation 5, Xbox Series X|S, PC, Stadia and Luna.', 46, now(), 7),
(8, 4, 'Top 10 Outstanding Curved Free Kicks', 'free_kicks.mp4', 'Thank you for watching! If you enjoyed, please Subscribe us', 90, now(), 15),
(9, 4, 'The Visit', 'the_visit.mp4', 'This 8 minute tragic-comedy by Conrad Tambour tells the story of an old woman, who, to the horror of her son, is cooking up a meal in the middle of the night for her long-deceased friends.', 90, now(), 26),
(10, 1, 'Computer Vision with Python', 'python_cv.mp4', 'Learn advanced computer vision using Python in this full course.', 105, now(), 30);

INSERT INTO channel(channel_id, userid, name, description, creation_date)
VALUES (1, 1, 'Programming Tutorials', 'tutorials about computer uploads in this channel', now());

INSERT INTO channel_video(channel_id, video_id)
VALUES (1,1), (1,2), (1,10);

INSERT INTO channel_subscription(channel_id, userid)
VALUES (1, 2), (1,3);

INSERT INTO comments(comment_id, userid, video_id, parent_id, comment, submit_date)
VALUES
(1, 2, 2, null, 'Lorem ipsum dolor sit amet', now()),
(2, 4, 2, 1, 'qui officia deserunt mollit anim', now()),
(3, 2, 2, null, 'elit esse cillum dolore eu fugiat nulla', now()),
(4, 2, 2, null, ' exercitation ullamco laboris nisi ut aliquip ex e', now());

INSERT INTO video_score (video_id, userid, score)
VALUES
(1,2,'like'),
(1,3,'like'),
(1,4,'dislike'),
(9,1,'dislike'),
(9,2,'dislike'),
(9,3,'dislike'),
(7,3,'like'),
(3,3,'like');

INSERT INTO playlist(list_id, userid, name, is_public)
VALUES (50, 1, 'animations', true);

INSERT INTO playlist_video(list_id, video_id)
VALUES (50,5), (50,6), (50,9);

COMMIT;
