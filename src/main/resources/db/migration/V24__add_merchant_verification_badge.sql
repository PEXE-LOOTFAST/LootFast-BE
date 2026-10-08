ALTER TABLE merchant_profiles
    ADD COLUMN verified_at TIMESTAMP WITH TIME ZONE;

UPDATE merchant_profiles profile
SET verified_at = application.reviewed_at
FROM merchant_applications application
WHERE application.user_id = profile.user_id
  AND application.status = 'APPROVED'
  AND application.reviewed_at IS NOT NULL;
