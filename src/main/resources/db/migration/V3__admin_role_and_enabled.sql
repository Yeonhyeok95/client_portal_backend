-- V3: 관리자(ADMIN) 역할 허용 + 계정 비활성화(soft delete) 컬럼 (2026-07-17)
-- users_role_check가 ('CLIENT','ADVISOR')로 고정돼 있어 ADMIN을 넣으려면 재생성 필요.
alter table users drop constraint users_role_check;
alter table users add constraint users_role_check check (role in ('CLIENT', 'ADVISOR', 'ADMIN'));

-- enabled=false면 로그인 차단 (데이터는 보존). 기존 행은 전부 활성으로 backfill.
alter table users add column enabled boolean not null default true;
