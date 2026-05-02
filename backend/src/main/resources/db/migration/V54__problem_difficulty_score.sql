ALTER TABLE problem ADD COLUMN IF NOT EXISTS difficulty_score NUMERIC(3,2);
ALTER TABLE problem ADD COLUMN IF NOT EXISTS auto_generated BOOLEAN DEFAULT false;

COMMENT ON COLUMN problem.difficulty_score IS 'Calibrated difficulty score 0.0-1.0, initially LLM-estimated then data-driven';
COMMENT ON COLUMN problem.auto_generated IS 'True for AI-generated variant problems from TransferVerifier';
