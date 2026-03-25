package io.autocrypt.jwlee.cowork;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.shell.DefaultShellApplicationRunner;
import org.springframework.test.context.ActiveProfiles;

/**
 * CLI 기반(Spring Shell + Embabel) 애플리케이션의 
 * 블로킹 I/O(터미널) 문제를 우회하기 위한 통합 테스트 베이스 클래스입니다.
 * 모든 통합 테스트는 이 클래스를 상속받으면 됩니다.
 */
@SpringBootTest
@ActiveProfiles({"gemini", "test"})
public abstract class BaseIntegrationTest {

    // Spring Shell의 메인 실행기를 모킹하여 애플리케이션 시작 시 터미널 입력을 대기하는 것을 방지
    @MockBean
    protected DefaultShellApplicationRunner defaultShellApplicationRunner;
}