-- Store contract_type as VARCHAR(20) for consistency with JPA @Enumerated(EnumType.STRING)
ALTER TABLE users
  MODIFY contract_type VARCHAR(20) NOT NULL;

-- Normalize legacy ENUM value to enum name (MINIJOB -> MINI_JOB)
UPDATE users SET contract_type = 'MINI_JOB' WHERE contract_type = 'MINIJOB';
