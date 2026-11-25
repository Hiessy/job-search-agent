-- Applications the user decided to track, and the write requests waiting for their OK.

create table job_application (
    id          bigserial primary key,
    company     varchar(200)  not null,
    role        varchar(200)  not null,
    posting_url varchar(2048),
    verdict     varchar(32),
    score       integer check (score between 0 and 100),
    status      varchar(16)   not null default 'SAVED',
    notes       varchar(1000),
    created_at  timestamptz   not null default now()
);

create unique index job_application_posting_url_uq on job_application (posting_url) where posting_url is not null;

-- A tool asked to write something. Nothing happens until a human approves it through the REST API.
create table pending_action (
    id             bigserial primary key,
    tool           varchar(64)   not null,
    summary        varchar(500)  not null,
    payload        text          not null,
    status         varchar(16)   not null default 'PENDING',
    requested_by   varchar(16)   not null,
    created_at     timestamptz   not null default now(),
    decided_at     timestamptz,
    application_id bigint references job_application (id),
    version        bigint        not null default 0
);

create index pending_action_status_idx on pending_action (status);
