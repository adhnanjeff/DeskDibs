-- Password material for the local (development) identity provider.
--
-- Nullable on purpose and forever: under Microsoft Entra ID nobody has a password here at
-- all — the token is minted by the tenant and this column stays NULL for every user. A NOT
-- NULL column would make the production identity provider impossible to switch on.
--
-- BCrypt output is a fixed 60 characters ($2a$<cost>$<22-char salt><31-char hash>);
-- varchar(100) leaves room for a longer prefix or a future cost increase without another
-- migration. Nothing but a hash ever goes in here — no plaintext, no reversible encoding.

ALTER TABLE app_user
    ADD COLUMN password_hash varchar(100);

COMMENT ON COLUMN app_user.password_hash IS
    'BCrypt hash, local development provider only. NULL for every Entra-authenticated user.';
