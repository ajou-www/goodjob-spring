package com.www.goodjob.integrate;
import com.www.goodjob.util.ClaudeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnableConfigurationProperties
@SpringBootTest(classes = ClaudeClient.class)
class ClaudeClientTest {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClientTest.class);

    private ClaudeClient claudeClient;

    @Value("${anthropic.api-key}")
    private String apiKey;

    @BeforeEach
    void setUp(){
        this.claudeClient= new ClaudeClient(apiKey);
    }

    // --- 테스트용 샘플 데이터 ---
    private static final String SAMPLE_CV = """
            # 김개발 | 주니어 백엔드 개발자

            ## 연락처
            - Email: dev.kim@example.com
            - GitHub: https://github.com/dev-kim

            ## 기술 스택
            - **언어**: Java, Python
            - **프레임워크**: Spring Boot, JPA, QueryDSL
            - **데이터베이스**: MySQL, Redis
            - **기타**: Docker, Git, Jenkins

            ## 주요 프로젝트 경험
            ### 1. 중고거래 플랫폼 (팀 프로젝트)
            - **기간**: 2024.01 ~ 2024.06
            - **역할**: 백엔드 개발
            - **주요 내용**:
              - Spring Boot와 JPA를 이용한 RESTful API 서버 구축
              - 상품 등록, 검색, 실시간 채팅 기능 개발
              - Redis를 활용한 인기 검색어 캐싱 처리로 검색 속도 30% 개선
              - Docker를 이용한 개발 환경 구축 및 배포 자동화 경험

            ## 학력
            - 멋쟁이대학교 컴퓨터공학과 (2018 ~ 2024)
            """;

    private static final String SAMPLE_JOB_DESCRIPTION = """
            # 백엔드 주니어 개발자 채용

            ## 주요 업무
            - 신규 서비스 API 서버 개발 및 운영
            - 기존 시스템 유지보수 및 성능 개선
            - 대규모 트래픽 처리 및 안정적인 서비스 운영

            ## 자격 요건
            - Java, Spring Boot 기반 개발 경험
            - JPA, QueryDSL 등 ORM 사용 능력
            - MySQL 등 RDBMS 사용 경험
            - Docker, K8s 등 컨테이너 환경 경험자 우대
            - Kafka, RabbitMQ 등 메시징 큐 경험자 우대
            """;

    private Path writeHtmlReport(String feedback ) throws IOException {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path outDir = Paths.get("src", "test/java/com/www/goodjob/integrate/feedback");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("feedback-" + ts + ".html");
        Files.writeString(outFile, feedback, StandardCharsets.UTF_8);
        return outFile;
    }


    private void openInBrowserIfPossible(Path file) {
        // CI나 서버 환경에서 브라우저 열기 시도 방지

        try {
            Desktop.getDesktop().browse(file.toUri());
        } catch (Exception ignored) {
            // 브라우저 열기 실패 시에도 테스트는 통과 (파일만 생성)
        }

    }

    @Test
    @DisplayName("이력서와 채용 공고를 바탕으로 피드백을 생성한다")
    void generateFeedback_3_Test() {

        this.claudeClient= new ClaudeClient(apiKey);
        // when
        String feedback = claudeClient.generateFeedback(SAMPLE_CV, SAMPLE_JOB_DESCRIPTION);

        // then
        assertNotNull(feedback);
        try{
            log.info(feedback);
            Path report = writeHtmlReport(feedback);
            log.info(report.toString());
            openInBrowserIfPossible(report);
        }catch(Exception e){
            e.printStackTrace();
        }

        // 로컬 환경이면 기본 브라우저로 열기 (CI/헤드리스는 건너뜀)

    }
    @Test
    @DisplayName("이력서를 바탕으로 핵심 내용을 요약한다")
    void generateCvSummary_Test() {
        // when
        String summary = claudeClient.generateCvSummary(SAMPLE_CV);

        // then
        assertNotNull(summary);
        System.out.println("✅ [CV 요약 결과]");
        System.out.println("--------------------------------------------------");
        System.out.println(summary);
        System.out.println("--------------------------------------------------");
    }
}