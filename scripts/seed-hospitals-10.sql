INSERT INTO svc.hospitals (
  id, hospital_code, hospital_name, hospital_type, city, state, country,
  email, primary_contact, registration_number, status, subscription_plan,
  registration_json, created_at, updated_at
) VALUES
(10002, '10002', 'Sunrise Care Hospital', 'Multi-Specialty', 'Mumbai', 'Maharashtra', 'India', 'admin@sunrisecare.example', '9820010002', 'REG-10002', 'ACTIVE', 'Standard', '{"hospitalId":10002,"hospitalCode":"10002","hospitalName":"Sunrise Care Hospital","city":"Mumbai"}', NOW(), NOW()),
(10003, '10003', 'City Heart Institute', 'Specialty', 'Pune', 'Maharashtra', 'India', 'info@cityheart.example', '9820010003', 'REG-10003', 'ACTIVE', 'Premium', '{"hospitalId":10003,"hospitalCode":"10003","hospitalName":"City Heart Institute","city":"Pune"}', NOW(), NOW()),
(10004, '10004', 'Green Valley Clinic', 'Clinic', 'Bengaluru', 'Karnataka', 'India', 'contact@greenvalley.example', '9820010004', 'REG-10004', 'PENDING_VERIFICATION', 'Basic', '{"hospitalId":10004,"hospitalCode":"10004","hospitalName":"Green Valley Clinic","city":"Bengaluru"}', NOW(), NOW()),
(10005, '10005', 'Apollo Metro Hospital', 'Multi-Specialty', 'Chennai', 'Tamil Nadu', 'India', 'desk@apollometro.example', '9820010005', 'REG-10005', 'ACTIVE', 'Premium', '{"hospitalId":10005,"hospitalCode":"10005","hospitalName":"Apollo Metro Hospital","city":"Chennai"}', NOW(), NOW()),
(10006, '10006', 'Lotus Women Hospital', 'Specialty', 'Hyderabad', 'Telangana', 'India', 'care@lotuswomen.example', '9820010006', 'REG-10006', 'ACTIVE', 'Standard', '{"hospitalId":10006,"hospitalCode":"10006","hospitalName":"Lotus Women Hospital","city":"Hyderabad"}', NOW(), NOW()),
(10007, '10007', 'Riverfront General', 'General', 'Ahmedabad', 'Gujarat', 'India', 'help@riverfront.example', '9820010007', 'REG-10007', 'ACTIVE', 'Basic', '{"hospitalId":10007,"hospitalCode":"10007","hospitalName":"Riverfront General","city":"Ahmedabad"}', NOW(), NOW()),
(10008, '10008', 'Hilltop Pediatric Center', 'Specialty', 'Jaipur', 'Rajasthan', 'India', 'peds@hilltop.example', '9820010008', 'REG-10008', 'PENDING_VERIFICATION', 'Standard', '{"hospitalId":10008,"hospitalCode":"10008","hospitalName":"Hilltop Pediatric Center","city":"Jaipur"}', NOW(), NOW()),
(10009, '10009', 'Ocean View Medical', 'Multi-Specialty', 'Kochi', 'Kerala', 'India', 'front@oceanview.example', '9820010009', 'REG-10009', 'ACTIVE', 'Premium', '{"hospitalId":10009,"hospitalCode":"10009","hospitalName":"Ocean View Medical","city":"Kochi"}', NOW(), NOW()),
(10010, '10010', 'Central Trauma Hospital', 'Trauma', 'Lucknow', 'Uttar Pradesh', 'India', 'er@centraltrauma.example', '9820010010', 'REG-10010', 'ACTIVE', 'Enterprise', '{"hospitalId":10010,"hospitalCode":"10010","hospitalName":"Central Trauma Hospital","city":"Lucknow"}', NOW(), NOW()),
(10011, '10011', 'Meadow Ortho Clinic', 'Clinic', 'Indore', 'Madhya Pradesh', 'India', 'ortho@meadow.example', '9820010011', 'REG-10011', 'INACTIVE', 'Basic', '{"hospitalId":10011,"hospitalCode":"10011","hospitalName":"Meadow Ortho Clinic","city":"Indore"}', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
