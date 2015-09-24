DROP TABLE IF EXISTS registrations;
CREATE TABLE registrations 
(
    id int unsigned not null auto_increment primary key,
    acraID varchar(36),
    model varchar(128),
    application varchar(128),
    application_version varchar(128),
    access_token varchar(44),
    timestamp timestamp default current_timestamp
);

DROP TABLE IF EXISTS measurements;
CREATE TABLE measurements 
(
    id int unsigned not null auto_increment primary key,
    key varchar(128),
    value float,
    registration_id int unsigned not null,
    timestamp timestamp default current_timestamp
);

DROP TABLE IF EXISTS aggregates;
CREATE TABLE aggregates 
(
    id int unsigned not null auto_increment primary key,
    key varchar(128),
    average float,
    variance float,
    count integer,
    registration_id int unsigned not null,
    timestamp timestamp default current_timestamp
);
