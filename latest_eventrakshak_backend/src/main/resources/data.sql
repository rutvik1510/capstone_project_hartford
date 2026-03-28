-- 1. Initialize Roles
MERGE INTO roles (role_id, role_name) KEY(role_name) VALUES (1, 'ADMIN');
MERGE INTO roles (role_id, role_name) KEY(role_name) VALUES (2, 'CUSTOMER');
MERGE INTO roles (role_id, role_name) KEY(role_name) VALUES (3, 'UNDERWRITER');
MERGE INTO roles (role_id, role_name) KEY(role_name) VALUES (4, 'CLAIMS_OFFICER');

-- 2. Initialize Admin User (Password: admin123)
MERGE INTO users (user_id, company_name, email, full_name, is_active, password, phone) 
KEY(email) 
VALUES (1, 'RK Events', 'admin@eventguard.com', 'Rahul Kumar', true, '$2a$10$ELopHLaLJ2sTob3MamrG4eGjirL0lldKBjBkwGxFl53kGAmGVmgei', '9876543210');

-- 3. Link Admin to Role
MERGE INTO user_role (user_id, role_id) KEY(user_id, role_id) VALUES (1, 1);

-- 4. Initialize 10 Professional Policies (logically structured)
-- MUSIC POLICIES (Domain: OUTDOOR_MUSIC_CONCERT)
MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (1, 3.0, 'Basic theft protection for small cafe or pub performances.', 'OUTDOOR_MUSIC_CONCERT', true, 50000.0, 'Micro Gigs (Music)', false, false, true, false);

MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (2, 5.0, 'Adds fire & electrical damage coverage for small indoor venues.', 'OUTDOOR_MUSIC_CONCERT', true, 200000.0, 'Silver Guard (Music)', false, true, true, false);

MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (3, 8.0, 'Professional outdoor coverage including rain and wind protection.', 'OUTDOOR_MUSIC_CONCERT', true, 1000000.0, 'Gold Secure (Music)', false, true, true, true);

MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (4, 12.0, 'Full protection for festivals, including artist cancellation.', 'OUTDOOR_MUSIC_CONCERT', true, 5000000.0, 'Platinum Elite (Music)', true, true, true, true);

MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (5, 15.0, 'Stadium-level shield including stage and structural failure.', 'OUTDOOR_MUSIC_CONCERT', true, 30000000.0, 'Starlight Protection (Music)', true, true, true, true);

-- CORPORATE POLICIES (Domain: CORPORATE_TECH_CONFERENCE)
MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (6, 2.5, 'Basic seminar liability for internal company workshops.', 'CORPORATE_TECH_CONFERENCE', true, 100000.0, 'Micro Seminar (Corp)', false, false, false, false);

MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (7, 4.0, 'Standard coverage for theft of AV equipment and laptops.', 'CORPORATE_TECH_CONFERENCE', true, 500000.0, 'Silver Guard (Corp)', false, false, true, false);

MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (8, 7.0, 'High-value tech protection plus speaker cancellation coverage.', 'CORPORATE_TECH_CONFERENCE', true, 2500000.0, 'Gold Secure (Corp)', true, false, true, false);

MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (9, 10.0, 'Full protection for international tech expos and keynotes.', 'CORPORATE_TECH_CONFERENCE', true, 10000000.0, 'Platinum Elite (Corp)', true, true, true, true);

MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (10, 14.0, 'Maximum enterprise coverage including data and IP security.', 'CORPORATE_TECH_CONFERENCE', true, 25000000.0, 'Global Expo Shield (Corp)', true, true, true, true);

-- NEW MUSIC POLICIES
MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (11, 6.5, 'Ideal for mid-sized outdoor indie band tours and local festivals.', 'OUTDOOR_MUSIC_CONCERT', true, 500000.0, 'Indie Festival Lite', false, false, true, true);

MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (12, 9.5, 'Optimized for high-energy EDM shows with pyro and laser risks.', 'OUTDOOR_MUSIC_CONCERT', true, 2500000.0, 'EDM Night Pulse', false, true, true, true);

MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (13, 18.0, 'Elite stadium-level coverage for international artist tours.', 'OUTDOOR_MUSIC_CONCERT', true, 50000000.0, 'Diamond Arena Shield', true, true, true, true);

-- NEW CORPORATE POLICIES
MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (14, 5.5, 'Focuses on protecting high-value AV gear and speaker booking fees.', 'CORPORATE_TECH_CONFERENCE', true, 1500000.0, 'Keynote Guardian', true, false, true, false);

MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (15, 8.5, 'Comprehensive networking liability for high-stakes investor summits.', 'CORPORATE_TECH_CONFERENCE', true, 5000000.0, 'Venture Meet Pro', true, true, true, false);

MERGE INTO policies (policy_id, base_rate, description, domain, is_active, max_coverage_amount, policy_name, covers_cancelation, covers_fire, covers_theft, covers_weather) 
KEY(policy_name) VALUES (16, 16.0, 'The ultimate enterprise shield for global-scale industry conventions.', 'CORPORATE_TECH_CONFERENCE', true, 100000000.0, 'Enterprise Mega Expo', true, true, true, true);

-- 5. Sync Sequences
ALTER TABLE users ALTER COLUMN user_id RESTART WITH (SELECT MAX(user_id) + 1 FROM users);
ALTER TABLE roles ALTER COLUMN role_id RESTART WITH (SELECT MAX(role_id) + 1 FROM roles);
ALTER TABLE policies ALTER COLUMN policy_id RESTART WITH (SELECT MAX(policy_id) + 1 FROM policies);
