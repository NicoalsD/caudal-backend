-- V48: an unconfirmed TOTP factor can be replaced (docs/Seguridad.md, section 2.4).
-- A person who lost the QR code before confirming starts again: the application deletes the
-- unconfirmed row and inserts a new one. The secret column stays without UPDATE, so a stored
-- secret is never overwritten in place; the use case only deletes rows with confirmed_at IS NULL.

GRANT DELETE ON iam.mfa_factors TO caudal_app;
