-- V5: 뉴스 카드 썸네일 (2026-07-19)
-- 이미지를 제공하는 피드만 수집하도록 정책 변경 (CNBC·Tax Foundation 제외).
-- 기존 행은 전부 이미지가 없는 구피드 기사라 비우고 시작한다 — 다음 조회 때 자동 재수집됨.
delete from news_articles;

alter table news_articles add column image_url varchar(1000) not null;
