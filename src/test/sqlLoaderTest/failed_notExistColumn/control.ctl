OPTIONS (
SKIP = 1,
)
LOAD DATA
CHARACTERSET AL32UTF8
TRUNCATE
PRESERVE BLANKS
INTO TABLE PERSON
FIELDS
TERMINATED BY ','
OPTIONALLY ENCLOSED BY '"'
(
LINE_NUMBER RECNUM,
PEOPLE_ID,
AGE,
NAME
)