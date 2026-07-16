package com.tsaptest.backend;

import com.tsaptest.backend.testutil.JwtTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 컨텍스트 스모크 테스트 — 전체 빈 배선이 실제로 조립되는지 확인.
 * 실 DB 대신 H2 인메모리를 쓴다 (NON_KEYWORDS=VALUE: accounts/holdings의
 * value 컬럼이 H2 예약어와 충돌하는 것 방지). Flyway SQL은 Postgres 전용이라
 * 끄고, 스키마는 엔티티 기준(create-drop)으로 만든다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smoke;NON_KEYWORDS=VALUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=" + JwtTestSupport.SECRET,
        "app.twofa.enabled=false"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
