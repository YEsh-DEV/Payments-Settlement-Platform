-- ==============================================================================
-- PayCore Multi-Tenant Database Initialization Script
-- Executed on container first boot by PostgreSQL entrypoint.
-- ==============================================================================

-- Create Databases
CREATE DATABASE identity;
CREATE DATABASE ledger;
CREATE DATABASE settlement;

-- Create Dedicated Roles (Principle of Least Privilege)
CREATE USER identity_user WITH ENCRYPTED PASSWORD 'identity_dev_password';
CREATE USER ledger_user WITH ENCRYPTED PASSWORD 'ledger_dev_password';
CREATE USER settlement_user WITH ENCRYPTED PASSWORD 'settlement_dev_password';

-- Grant Privileges strictly per Database
GRANT ALL PRIVILEGES ON DATABASE identity TO identity_user;
GRANT ALL PRIVILEGES ON DATABASE ledger TO ledger_user;
GRANT ALL PRIVILEGES ON DATABASE settlement TO settlement_user;

-- Connect to identity and grant schema permissions
\connect identity
GRANT ALL ON SCHEMA public TO identity_user;
ALTER SCHEMA public OWNER TO identity_user;

-- Connect to ledger and grant schema permissions
\connect ledger
GRANT ALL ON SCHEMA public TO ledger_user;
ALTER SCHEMA public OWNER TO ledger_user;

-- Connect to settlement and grant schema permissions
\connect settlement
GRANT ALL ON SCHEMA public TO settlement_user;
ALTER SCHEMA public OWNER TO settlement_user;
