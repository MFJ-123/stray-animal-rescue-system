-- Preserve every accepted submission key, including keys that reuse an existing pending application.
CREATE TABLE volunteer_submission_receipt (
 user_id BIGINT NOT NULL,
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 application_id BIGINT NOT NULL,
 PRIMARY KEY(user_id,request_key),
 FOREIGN KEY(user_id) REFERENCES account(id),
 FOREIGN KEY(application_id) REFERENCES volunteer_request(id)
) ENGINE=InnoDB;
INSERT INTO volunteer_submission_receipt(user_id,request_key,request_hash,application_id)
SELECT user_id,request_key,request_hash,id FROM volunteer_request;
