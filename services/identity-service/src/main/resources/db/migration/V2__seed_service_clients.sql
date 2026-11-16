-- V2__seed_service_clients.sql: Seed default machine-to-machine service client for settlement-service

INSERT INTO service_clients (id, client_id, secret_hash, roles, enabled, created_at)
VALUES (
    gen_random_uuid(),
    'settlement-service',
    '$2a$12$Cobx6/L11TcqzzHLzqQCq.AB8xkjdhbAdqt23yD9zrwV2wHq.Hh/S',
    ARRAY['SERVICE'],
    TRUE,
    NOW()
)
ON CONFLICT (client_id) DO NOTHING;
