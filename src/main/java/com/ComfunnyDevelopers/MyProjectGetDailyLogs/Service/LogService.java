package com.ComfunnyDevelopers.MyProjectGetDailyLogs.Service;

import com.ComfunnyDevelopers.MyProjectGetDailyLogs.System.Config.SshConfig;
import com.jcraft.jsch.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/**
 * LogService는 로그 관련 작업을 수행하는 서비스 클래스입니다.
 * 주요 기능:
 * - 로컬의 logs 폴더(서버 타입에 따라 logs/web 또는 logs/deliveryapp)에 저장된 파일에서
 *   전날(또는 특정 날짜)의 로그를 읽어 반환
 * - SSH를 통해 원격 서버의 실시간 로그를 스트리밍 (SSE 방식)
 * - 원격 서버에서 로그 파일을 다운로드 받아 로컬에 저장
 * - 파일 존재 여부를 확인
 */
@Service
public class LogService {
    private final SshConfig sshConfig;

    /**
     * SshConfig를 생성자 주입받아, SSH 접속 관련 설정을 관리합니다.
     *
     * @param sshConfig SSH 접속 및 로그 경로 관련 설정
     */
    public LogService(SshConfig sshConfig) {
        this.sshConfig = sshConfig;
    }

    /**
     * 서버 타입에 따라 원격 로그 경로를 선택합니다.
     * 만약 서버 타입이 "deliveryapp"이면 배송기사앱 로그 경로를, 그렇지 않으면 웹 서버 로그 경로를 반환합니다.
     *
     * @param serverType 서버 타입 ("web" 또는 "deliveryapp")
     * @return 선택된 서버에 따른 원격 로그 경로
     */
    private String getRemoteLogPath(String serverType) {
        if ("deliveryapp".equalsIgnoreCase(serverType)) {
            return sshConfig.getDeliveryappLogPath();
        }
        // 기본값은 웹 서버 로그 경로
        return sshConfig.getWebLogPath();
    }

    /**
     * 서버 타입에 따라 로컬 저장 폴더 경로를 선택합니다.
     * "deliveryapp"이면 logs/deliveryapp, 그 외에는 logs/web 폴더를 사용합니다.
     *
     * @param serverType 서버 타입 ("web" 또는 "deliveryapp")
     * @return 선택된 서버에 따른 로컬 저장 폴더 경로
     */
    private String getLocalFolder(String serverType) {
        if ("deliveryapp".equalsIgnoreCase(serverType)) {
            return "logs/deliveryapp";
        }
        return "logs/web";
    }

    /**
     * 지정한 날짜와 서버 타입에 해당하는 로그를 로컬 파일에서 읽어 반환합니다.
     * 파일 경로는 {로컬 폴더}/log_{date}.txt 형식입니다.
     * 이 메서드는 비동기적으로 CompletableFuture를 통해 로그 내용을 반환합니다.
     *
     * @param date       날짜 (YYYYMMDD 형식)
     * @param serverType 서버 타입 ("web" 또는 "deliveryapp")
     * @return 로그 내용 문자열을 담은 CompletableFuture
     */
    public CompletableFuture<String> getLogs(String date, String serverType) {
        return CompletableFuture.supplyAsync(() -> {
            // 서버 타입에 따른 로컬 저장 폴더 경로 결정
            String localFolder = getLocalFolder(serverType);
            // 파일 경로 형식: logs/{폴더}/log_{date}.txt
            String filePath = localFolder + "/log_" + date + ".txt";
            File file = new File(filePath);
            if (!file.exists()) {
                return "해당 날짜의 로그 파일이 존재하지 않습니다.";
            }
            try {
                // 파일 내용을 UTF-8로 읽어서 문자열로 반환
                return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("로그 파일 읽기 실패", e);
            }
        });
    }

    /**
     * 공통 메서드: 원격 서버에서 SSH 명령어를 실행하고, 그 결과를 문자열로 반환합니다.
     * 내부적으로 JSch 라이브러리를 사용하여 SSH 연결을 설정하고 명령어를 실행합니다.
     *
     * @param command 실행할 SSH 명령어
     * @return 명령어 실행 결과 문자열, 에러 발생 시 에러 메시지 포함
     */
    private String executeSshCommand(String command) {
        StringBuilder result = new StringBuilder();
        try {
            // JSch 객체 생성 및 SSH 세션 설정
            JSch jsch = new JSch();
            Session session = jsch.getSession(sshConfig.getUser(), sshConfig.getHost(), sshConfig.getPort());
            session.setPassword(sshConfig.getPassword());
            // 호스트 키 검증 건너뛰기 설정
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            // exec 채널을 열어 명령어 실행
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);

            // 명령어 실행 결과를 읽기 위한 InputStream 획득
            InputStream in = channel.getInputStream();
            channel.connect();

            // BufferedReader를 통해 결과를 한 줄씩 읽음
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }

            // 채널과 세션 종료
            channel.disconnect();
            session.disconnect();
        } catch (Exception e) {
            return "❌ 로그를 가져오는 중 오류 발생: " + e.getMessage();
        }
        return result.toString();
    }

    /**
     * (중복 메서드)
     * 전날 또는 특정 날짜의 로그를 로컬 파일("logs/log_{date}.txt")에서 읽어 반환합니다.
     * 파일이 없으면 "해당 날짜의 로그 파일이 존재하지 않습니다." 메시지를 반환합니다.
     *
     * @param date 날짜 (YYYYMMDD 형식)
     * @return 로그 내용 문자열을 담은 CompletableFuture
     */
    public CompletableFuture<String> getLogs(String date) {
        return CompletableFuture.supplyAsync(() -> {
            String filePath = "logs/log_" + date + ".txt";
            File file = new File(filePath);
            if (!file.exists()) {
                return "해당 날짜의 로그 파일이 존재하지 않습니다.";
            }
            try {
                return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("로그 파일 읽기 실패", e);
            }
        });
    }

    /**
     * 지정한 duration(초) 동안의 로그를 가져오는 폴링 메서드입니다.
     * 현재 예제에서는 duration 파라미터를 사용하지 않고, 단순하게 원격 로그 파일의 최근 100줄을 반환합니다.
     * 실제 구현 시에는 duration 값을 활용하여 시간 범위를 계산할 수 있습니다.
     *
     * @param duration   가져올 로그 기간(초) (현재 미사용)
     * @param serverType 서버 타입 ("web" 또는 "deliveryapp")
     * @return 최근 로그 내용 문자열
     */
    public String pollLogs(int duration, String serverType) {
        // 선택된 서버 타입에 따른 원격 로그 경로 가져오기
        String remotePath = getRemoteLogPath(serverType);
        // tail 명령어를 사용하여 원격 로그 파일의 최근 100줄을 가져옴
        String command = String.format("tail -n 100 %s", remotePath);
        return executeSshCommand(command);
    }

    /**
     * 원격 서버에서 지정한 날짜의 로그를 다운로드 받아, 로컬의 logs 폴더에 저장합니다.
     * 서버 타입에 따라 로컬 저장 폴더가 다르게 설정됩니다.
     * 파일 형식: {localFolder}/log_{date}.txt
     *
     * @param date       날짜 (YYYYMMDD 형식)
     * @param serverType 서버 타입 ("web" 또는 "deliveryapp")
     * @return 저장된 파일 경로를 반환 (저장 실패 시 null 반환)
     */
    public String downloadAndSaveLog(String date, String serverType) {
        // 선택된 서버 타입에 따른 원격 로그 경로를 가져옴
        String remotePath = getRemoteLogPath(serverType);
        // grep 명령어를 사용하여 해당 날짜에 포함된 로그를 추출
        String command = String.format("grep '%s' %s", date, remotePath);
        StringBuilder result = new StringBuilder();
        try {
            // SSH 연결 설정 및 명령어 실행
            JSch jsch = new JSch();
            Session session = jsch.getSession(sshConfig.getUser(), sshConfig.getHost(), sshConfig.getPort());
            session.setPassword(sshConfig.getPassword());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);

            // 명령어 실행 결과를 읽기 위해 InputStream 사용
            InputStream in = channel.getInputStream();
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
            // 채널과 세션 종료
            channel.disconnect();
            session.disconnect();

            // 서버 타입에 따른 로컬 저장 폴더 가져오기
            String localFolder = getLocalFolder(serverType);
            File folder = new File(localFolder);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            // 파일 저장 경로 형식: {localFolder}/log_{date}.txt
            String filePath = localFolder + "/log_" + date + ".txt";
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
                writer.write(result.toString());
            }
            return filePath;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 지정한 날짜와 서버 타입에 해당하는 로그 파일을 Resource 객체로 반환합니다.
     * 파일이 존재하지 않을 경우, 먼저 downloadAndSaveLog 메서드를 호출하여 다운로드를 시도합니다.
     *
     * @param date       날짜 (YYYYMMDD 형식)
     * @param serverType 서버 타입 ("web" 또는 "deliveryapp")
     * @return Resource 객체 (파일이 없으면 null 반환)
     */
    public Resource getSavedLogFile(String date, String serverType) {
        // 서버 타입에 따른 로컬 저장 폴더 가져오기
        String localFolder = getLocalFolder(serverType);
        File folder = new File(localFolder);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        // 파일 경로 설정: {localFolder}/log_{date}.txt
        String filePath = localFolder + "/log_" + date + ".txt";
        File file = new File(filePath);
        if (!file.exists()) {
            // 파일이 없으면 먼저 다운로드를 시도
            String saved = downloadAndSaveLog(date, serverType);
            if (saved == null) return null;
        }
        return new FileSystemResource(file);
    }

    /**
     * 지정한 날짜와 서버 타입에 해당하는 로그 파일의 존재 여부를 확인합니다.
     * 파일이 존재하지 않으면 downloadAndSaveLog 메서드를 호출하여 다운로드를 시도합니다.
     *
     * @param date       날짜 (YYYYMMDD 형식)
     * @param serverType 서버 타입 ("web" 또는 "deliveryapp")
     * @return 파일 존재 여부 (true: 파일 존재, false: 파일 없음 또는 다운로드 실패)
     */
    public boolean checkFileExists(String date, String serverType) {
        // 서버 타입에 따른 로컬 저장 폴더 가져오기
        String localFolder = getLocalFolder(serverType);
        // 파일 경로 설정: {localFolder}/log_{date}.txt
        String filePath = localFolder + "/log_" + date + ".txt";
        File file = new File(filePath);
        if (!file.exists()) {
            // 파일이 없으면 다운로드 시도 후, 성공 여부를 반환
            String saved = downloadAndSaveLog(date, serverType);
            return saved != null;
        }
        return true;
    }
}
