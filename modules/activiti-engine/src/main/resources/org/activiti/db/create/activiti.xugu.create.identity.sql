CREATE TABLE ACT_ID_GROUP (
                              ID_ VARCHAR(64),
                              REV_ INTEGER,
                              NAME_ VARCHAR(255),
                              TYPE_ VARCHAR(255),
                              PRIMARY KEY (ID_)
);

CREATE TABLE ACT_ID_MEMBERSHIP (
                                   USER_ID_ VARCHAR(64),
                                   GROUP_ID_ VARCHAR(64),
                                   PRIMARY KEY (USER_ID_, GROUP_ID_)
);

CREATE TABLE ACT_ID_USER (
                             ID_ VARCHAR(64),
                             REV_ INTEGER,
                             FIRST_ VARCHAR(255),
                             LAST_ VARCHAR(255),
                             EMAIL_ VARCHAR(255),
                             PWD_ VARCHAR(255),
                             PICTURE_ID_ VARCHAR(64),
                             PRIMARY KEY (ID_)
);

CREATE TABLE ACT_ID_INFO (
                             ID_ VARCHAR(64),
                             REV_ INTEGER,
                             USER_ID_ VARCHAR(64),
                             TYPE_ VARCHAR(64),
                             KEY_ VARCHAR(255),
                             VALUE_ VARCHAR(255),
                             PASSWORD_ BLOB,
                             PARENT_ID_ VARCHAR(255),
                             PRIMARY KEY (ID_)
);

ALTER TABLE ACT_ID_MEMBERSHIP
    ADD CONSTRAINT ACT_FK_MEMB_GROUP
        FOREIGN KEY (GROUP_ID_)
            REFERENCES ACT_ID_GROUP (ID_);

ALTER TABLE ACT_ID_MEMBERSHIP
    ADD CONSTRAINT ACT_FK_MEMB_USER
        FOREIGN KEY (USER_ID_)
            REFERENCES ACT_ID_USER (ID_);