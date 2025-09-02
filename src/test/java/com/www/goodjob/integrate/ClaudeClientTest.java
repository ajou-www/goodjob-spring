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
        String feedbackPrompt = """
                        당신은 AI 이력서 평가자입니다.
                        아래에 제공된 이력서(CV)와 채용 공고(Job Description)를 바탕으로, 지원자가 해당 포지션에 적합한지를 평가한 피드백을 한글로 작성해주세요.
                        전체 피드백은 1800자 이내로 작성해주세요. 각 항목은 간결하면서도 핵심을 포함해주세요.
                        
                        피드백은 다음 세 항목으로만 구성되며, 반드시 아래 형식을 따라야 합니다:
                        
                        좋은 점:
                        - 이력서에서 채용 공고와 잘 부합하는 기술, 경험, 표현을 구체적인 예시와 함께 서술해주세요.
                        - 예시:
                          - **FastAPI, Docker, NGINX** 등을 활용한 서비스 배포 및 운영 경험은 **채용 공고에서 요구하는 백엔드 시스템 개발 및 API 개발 역량**과 직접적으로 부합합니다.
                          - 다양한 **데이터베이스(MySQL, MongoDB)를 활용한 경험**과 데이터베이스 마이그레이션 경험은 **대규모 데이터 처리 능력**을 보여줍니다.
                        
                        부족한 점:
                        - 채용 공고에서 요구하는 요건 중 이력서에 명확히 드러나지 않거나 부족해 보이는 부분을 지적해주세요.
                        - **강조가 필요한 부족한 부분은 `**강조**` 표시로 명확히 표현해주세요.**
                        
                        추가 팁:
                        - 경쟁력을 높이기 위해 추가하거나 보완하면 좋을 내용, 표현, 학습 또는 포트폴리오 개선 방향을 제안해주세요.
                        - 특히 **추가할 기술 스택, 실무 경험, 표현 개선 등**은 `**강조**` 형식으로 강조해주세요.
                        
                다음 지침을 **엄격히** 따르고, 최종 출력은 **HTML 조각(fragment)** 만 반환하세요. 다른 설명이나 머리말, 주석은 절대 포함하지 마세요.
                
                   요구사항 (반드시 지킴):
                   1. 전체 내용은 반드시 하나의 <div>로 감싸세요.
                   2. 출력은 세 개의 섹션을 반드시 포함해야 합니다: "좋은 점:", "부족한 점:", "추가 팁:" (순서 고정).
                   3.  각 섹션의 전체내용은 <div> 태그로 감싸고  class는 각각 "good-things", "bad-things", "tips" 로 지정하세요 
                        각 섹션의 제목은 <h2> 태그로 감싸주세요. 텍스트는 정확히 <h2>좋은 점:</h2>, <h2>부족한 점:</h2>, <h2>추가 팁:</h2> 로 표기합니다.
                   4. 각 섹션의 본문은 <ul> 내의 <li> 항목들로 작성하세요. 또 
                   5. 각 <li>의 텍스트 시작은 반드시 하이픈과 공백 "- "으로 시작해야 합니다.
                   6. 모든 문장은 존댓말로 작성하며, 구체적이고 실질적인 조언을 포함해야 합니다.
                   7. 강조할 키워드는 <strong> 태그로 감싸세요. 단, 어떤 style 속성도 사용하지 마세요.
                   8. 위의 규칙 외에는 어떤 내용도 추가하지 마세요. 반환 형식은 유효한 HTML 조각이어야 합니다.
                   """;
        String summaryPrompt="";
        this.claudeClient= new ClaudeClient(apiKey,feedbackPrompt, summaryPrompt);

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