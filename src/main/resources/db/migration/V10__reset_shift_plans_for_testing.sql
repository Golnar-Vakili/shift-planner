-- Reset shift planning data for testing: remove all shift plans and entries so the workflow can be tested from scratch.
-- Only affects shift_plans and shift_entries; users and other data are unchanged.
DELETE FROM shift_entries;
DELETE FROM shift_plans;
