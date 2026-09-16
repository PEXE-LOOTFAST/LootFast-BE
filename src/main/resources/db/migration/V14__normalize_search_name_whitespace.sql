UPDATE deals
SET search_name = regexp_replace(search_name, '[[:space:]]+', ' ', 'g')
WHERE search_name ~ '[[:space:]]{2,}';

UPDATE merchant_profiles
SET search_name = regexp_replace(search_name, '[[:space:]]+', ' ', 'g')
WHERE search_name ~ '[[:space:]]{2,}';
