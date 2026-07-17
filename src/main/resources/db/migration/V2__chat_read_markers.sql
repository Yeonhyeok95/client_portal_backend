-- 채팅 안읽음 배지용 읽음 마커.
-- "이 메시지 ID까지 읽었다"를 대화방마다 역할별로 1개씩 기록한다.
-- 타임스탬프 대신 메시지 ID를 쓰는 이유: ID는 단조증가라 같은 시각에 온
-- 메시지끼리의 순서 모호함이 없다. 0 = 아무것도 안 읽음.
alter table chat_conversations
    add column client_last_read_message_id  bigint not null default 0,
    add column advisor_last_read_message_id bigint not null default 0;
