-- Separate incomplete, source-checked findings from the final report contract.
ALTER TABLE analyses ADD COLUMN progress_json TEXT;
