-- Test only: gives caudal_app a throwaway password inside the disposable Testcontainers database,
-- so integration tests can run the whole application as the real least-privilege role (privileges
-- and row level security apply). Loaded only by the app-role test profile.
ALTER ROLE caudal_app PASSWORD 'test-only-role-password';
