INSERT INTO user_movie_list(user_id, name, type, is_default)
SELECT u.id, defaults.name, defaults.type, 1
  FROM user u
 CROSS JOIN (
       SELECT '想看' AS name, 'want_to_watch' AS type
       UNION ALL SELECT '在看', 'watching'
       UNION ALL SELECT '看过', 'watched'
 ) defaults
 WHERE u.is_deleted = 0
   AND NOT EXISTS (
       SELECT 1
         FROM user_movie_list existing
        WHERE existing.user_id = u.id
          AND existing.type = defaults.type
   );
