package com.ComfunnyDevelopers.MyProjectGetDailyLogs.Controller;

import com.ComfunnyDevelopers.MyProjectGetDailyLogs.Service.LogService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * LogController는 클라이언트 요청에 따라 로그 관련 작업을 처리하는 REST API 엔드포인트를 제공합니다.
 * 주요 기능은 다음과 같습니다:
 * - 전날 혹은 특정 날짜의 로그 내용을 반환 (비동기 방식, CompletableFuture 사용)
 * - 폴링 방식으로 최근 로그를 문자열로 반환 (GET /api/logs/poll)
 * - 로그 파일을 다운로드 (GET /api/logs/downloadFile)
 * - 로그 파일의 존재 여부를 확인 (GET /api/logs/fileStatus)
 */
@RestController
@RequestMapping("/api/logs")
public class LogController {
    private final LogService logService;

    /**
     * 생성자 주입을 통해 LogService 인스턴스를 주입받습니다.
     *
     * @param logService 로그 관련 작업을 수행하는 서비스 클래스
     */
    public LogController(LogService logService) {
        this.logService = logService;
    }

    /**
     * 지정한 날짜와 서버 타입의 로그 내용을 반환합니다.
     * 클라이언트는 날짜 (YYYYMMDD 형식)와 서버 타입("web" 또는 "deliveryapp")을 파라미터로 전송합니다.
     * 로그 내용은 비동기적으로 처리되어 CompletableFuture로 반환됩니다.
     *
     * @param date   날짜 (예: "20250305")
     * @param server 서버 타입 ("web" 또는 "deliveryapp")
     * @return 지정한 날짜의 로그 내용이 담긴 CompletableFuture<String>
     */
    @GetMapping
    public CompletableFuture<String> getLogs(@RequestParam String date, @RequestParam String server) {
        return logService.getLogs(date, server);
    }

    /**
     * 폴링 API 엔드포인트입니다.
     * 지정한 기간(duration, 초) 동안의 로그를 원격 서버에서 읽어와 최근 로그 100줄을 문자열로 반환합니다.
     * 이 예제에서는 duration 파라미터를 사용하지 않고 단순하게 최근 100줄을 반환합니다.
     *
     * 요청 예시: GET /api/logs/poll?duration=10&server=web
     *
     * @param duration 가져올 로그 기간(초) - 현재는 기능 미사용
     * @param server   서버 타입 ("web" 또는 "deliveryapp")
     * @return 최근 로그 내용 문자열
     */
    @GetMapping("/poll")
    public String pollLogs(@RequestParam int duration, @RequestParam String server) {
        return logService.pollLogs(duration, server);
    }

    /**
     * 지정한 날짜와 서버 타입의 로그 파일을 다운로드합니다.
     * 클라이언트가 이 API를 호출하면, 백엔드에서 해당 날짜의 로그 파일(Resource)을 찾아 클라이언트로 전송합니다.
     * 파일이 존재하지 않으면 404 Not Found 응답을 반환합니다.
     *
     * @param date   날짜 (YYYYMMDD 형식)
     * @param server 서버 타입 ("web" 또는 "deliveryapp")
     * @return 파일 리소스가 포함된 ResponseEntity
     */
    @GetMapping("/downloadFile")
    public ResponseEntity<Resource> downloadFile(@RequestParam String date, @RequestParam String server) {
        Resource resource = logService.getSavedLogFile(date, server);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        // Content-Disposition 헤더를 설정하여 클라이언트가 파일로 다운로드하도록 함
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=log_" + date + ".txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }

    /**
     * 지정한 날짜와 서버 타입의 로그 파일 존재 여부를 확인하는 API입니다.
     * 클라이언트는 이 API를 호출하여 해당 날짜에 로그 파일이 저장되어 있는지 확인할 수 있습니다.
     * 결과는 JSON 형식으로 { "exists": true } 또는 { "exists": false }가 반환됩니다.
     *
     * @param date   날짜 (YYYYMMDD 형식)
     * @param server 서버 타입 ("web" 또는 "deliveryapp")
     * @return 파일 존재 여부가 포함된 ResponseEntity<Map<String, Boolean>>
     */
    @GetMapping("/fileStatus")
    public ResponseEntity<Map<String, Boolean>> getFileStatus(@RequestParam String date, @RequestParam String server) {
        boolean exists = logService.checkFileExists(date, server);
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);
        return ResponseEntity.ok(response);
    }
}
