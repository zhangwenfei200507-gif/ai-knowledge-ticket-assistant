UPDATE users
SET role = 'ADMIN',
    token_version = COALESCE(token_version, 0) + 1,
    updated_time = CURRENT_TIMESTAMP
WHERE lower(email) = lower('zhangwenfei200507@gmail.com')
  AND role IS DISTINCT FROM 'ADMIN';
