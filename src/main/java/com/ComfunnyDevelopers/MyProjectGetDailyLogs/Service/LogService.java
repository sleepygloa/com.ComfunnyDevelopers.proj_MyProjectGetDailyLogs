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
import java.util.zip.GZIPInputStream;

/**
 * LogService는 로그 관련 작업을 수행하는 서비스 클래스입니다.
 * 주요 기능:
 * - 로컬의 logs 폴더(서버 타입에 따라 logs/web 또는 logs/deliveryapp)에 저장된 파일에서
 *   전날(또는 특정 날짜)의 로그를 읽어 반환 (deliveryapp의 경우 압축 파일에서 읽어옴)
 * - SSH를 통해 원격 서버의 실시간 로그를 스트리밍 (pollLogs)
 * - 원격 서버에서 로그 파일을 다운로드 받아 로컬에 저장
 * - 파일 존재 여부를 확인
 */
@Service
public class LogService {
    private final SshConfig sshConfig;

    public LogService(SshConfig sshConfig) {
        this.sshConfig = sshConfig;
    }

    /**
     * 실시간 로그 조회에서는 두 경우 모두 catalina.out 파일을 사용합니다.
     * (Tomcat 구동 시, "web"과 "deliveryapp" 모두 catalina.out을 실시간 로그로 사용)
     *
     * @param serverType 서버 타입 ("web" 또는 "deliveryapp")
     * @return 실시간 로그 파일 경로 (예: /usr/local/tomcat_app/logs/catalina.out)
     */
    private String getRemoteLogPath(String serverType) {
        return sshConfig.getWebLogPath();
    }

    /**
     * 서버 타입에 따라 로컬 저장 폴더 경로를 선택합니다.
     * "deliveryapp"이면 logs/deliveryapp, 그 외에는 logs/web 폴더를 사용합니다.
     *
     * @param serverType 서버 타입 ("web" 또는 "deliveryapp")
     * @return 선택된 로컬 폴더 경로
     */
    private String getLocalFolder(String serverType) {
        if ("deliveryapp".equalsIgnoreCase(serverType)) {
            return "logs/deliveryapp";
        }
        return "logs/web";
    }

    /**
     * 지정한 날짜와 서버 타입에 해당하는 로그를 로컬 파일에서 읽어 반환합니다.
     * - 웹 서버: {로컬 폴더}/log_{date}.txt
     * - deliveryapp: {로컬 폴더}/deliveryapp-{YYYY-MM-DD}.log.gz (압축 파일)
     *
     * @param date       날짜 (YYYYMMDD 형식)
     * @param serverType 서버 타입 ("web" 또는 "deliveryapp")
     * @return 로그 내용 문자열을 담은 CompletableFuture
     */
    public CompletableFuture<String> getLogs(String date, String serverType) {
        return CompletableFuture.supplyAsync(() -> {
            String localFolder = getLocalFolder(serverType);
            String fileName;
            if ("deliveryapp".equalsIgnoreCase(serverType)) {
                String formattedDate = date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6);
                fileName = "deliveryapp-" + formattedDate + ".log.gz";
            } else {
                fileName = "log_" + date + ".txt";
            }
            String filePath = localFolder + "/" + fileName;
            File file = new File(filePath);
            if (!file.exists()) {
                return "해당 날짜의 로그 파일이 존재하지 않습니다.";
            }
            try {
                if (fileName.endsWith(".gz")) {
                    try (FileInputStream fis = new FileInputStream(file);
                         GZIPInputStream gis = new GZIPInputStream(fis);
                         InputStreamReader isr = new InputStreamReader(gis, StandardCharsets.UTF_8);
                         BufferedReader br = new BufferedReader(isr)) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        return sb.toString();
                    }
                } else {
                    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new RuntimeException("로그 파일 읽기 실패", e);
            }
        });
    }

    /**
     * 공통 메서드: 원격 서버에서 SSH 명령어를 실행하고 그 결과를 문자열로 반환합니다.
     *
     * @param command 실행할 SSH 명령어
     * @return 명령어 실행 결과 문자열, 에러 발생 시 에러 메시지 포함
     */
    private String executeSshCommand(String command) {
        StringBuilder result = new StringBuilder();
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(sshConfig.getUser(), sshConfig.getHost(), sshConfig.getPort());
            session.setPassword(sshConfig.getPassword());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);

            InputStream in = channel.getInputStream();
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
            channel.disconnect();
            session.disconnect();
        } catch (Exception e) {
            return "❌ 로그를 가져오는 중 오류 발생: " + e.getMessage();
        }
        return result.toString();
    }

    /**
     * 지정한 시간 구간(startTime ~ endTime)의 실시간 로그를 필터링하여 반환합니다.
     * catalina.out 파일에서 각 로그 라인의 앞 23자 (예: "YYYY-MM-DD HH:mm:ss,SSS")를 기준으로 비교합니다.
     *
     * @param startTime  시작 시각 (예: "2025-03-26 12:48:00,000")
     * @param endTime    종료 시각 (예: "2025-03-26 12:48:09,999")
     * @param serverType 서버 타입 ("web" 또는 "deliveryapp") – 실시간 로그 조회는 항상 catalina.out 사용
     * @return 해당 시간 구간의 로그 내용 문자열
     */
    public String pollLogs(String startTime, String endTime, String serverType) {
        if ("deliveryapp".equalsIgnoreCase(serverType)) {
            // 실시간 로그 파일은 catalina.out (두 경우 모두 동일하게 사용)
            String remotePath = sshConfig.getDeliveryappLogPath();
            // awk 명령어를 사용하여 로그의 앞 23자를 추출하여 startTime 이상 endTime 이하인 줄만 출력
            String command = String.format(
                    "awk -v s=\"%s\" -v e=\"%s\" 'substr($0,1,23) >= s && substr($0,1,23) <= e' %s",
                    startTime, endTime, remotePath);
            return executeSshCommand(command);
        }
        // 실시간 로그 파일은 catalina.out (두 경우 모두 동일하게 사용)
        String remotePath = sshConfig.getWebLogPath();
        // awk 명령어를 사용하여 로그의 앞 23자를 추출하여 startTime 이상 endTime 이하인 줄만 출력
        String command = String.format(
                "awk -v s=\"%s\" -v e=\"%s\" 'substr($0,1,23) >= s && substr($0,1,23) <= e' %s",
                startTime, endTime, remotePath);
        return executeSshCommand(command);
    }

    /**
     * 원격 서버에서 지정한 날짜의 로그를 다운로드 받아 로컬의 logs 폴더에 저장합니다.
     * - deliveryapp: 압축된 로그 파일 (deliveryapp-YYYY-MM-DD.log.gz)
     * - 웹 서버: grep으로 해당 날짜의 로그를 추출하여 저장
     *
     * @param date       날짜 (YYYYMMDD 형식)
     * @param serverType 서버 타입 ("web" 또는 "deliveryapp")
     * @return 저장된 파일 경로 (실패 시 null)
     */
    public String downloadAndSaveLog(String date, String serverType) {
        String remotePath;
        String command;
        if ("deliveryapp".equalsIgnoreCase(serverType)) {
            String formattedDate = date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6);
            remotePath = sshConfig.getDeliveryappLogPath() + "-" + formattedDate + ".log.gz";
            command = String.format("cat %s", remotePath);
        } else {
            remotePath = getRemoteLogPath(serverType);
            command = String.format("grep '%s' %s", date, remotePath);
        }
        StringBuilder result = new StringBuilder();
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(sshConfig.getUser(), sshConfig.getHost(), sshConfig.getPort());
            session.setPassword(sshConfig.getPassword());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);

            InputStream in = channel.getInputStream();
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
            channel.disconnect();
            session.disconnect();

            String localFolder = getLocalFolder(serverType);
            File folder = new File(localFolder);
            if (!folder.exists()) {
                folder.mkdirs();
            }
            String fileName;
            if ("deliveryapp".equalsIgnoreCase(serverType)) {
                fileName = "deliveryapp-" + date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6) + ".log.gz";
            } else {
                fileName = "log_" + date + ".txt";
            }
            String filePath = localFolder + "/" + fileName;
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
     * 파일이 존재하지 않을 경우, 먼저 downloadAndSaveLog를 호출하여 다운로드 시도
     *
     * @param date       날짜 (YYYYMMDD 형식)
     * @param serverType 서버 타입 ("web" 또는 "deliveryapp")
     * @return Resource 객체 (없으면 null)
     */
    public Resource getSavedLogFile(String date, String serverType) {
        String localFolder = getLocalFolder(serverType);
        File folder = new File(localFolder);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        String fileName;
        if ("deliveryapp".equalsIgnoreCase(serverType)) {
            fileName = "deliveryapp-" + date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6) + ".log.gz";
        } else {
            fileName = "log_" + date + ".txt";
        }
        String filePath = localFolder + "/" + fileName;
        File file = new File(filePath);
        if (!file.exists()) {
            String saved = downloadAndSaveLog(date, serverType);
            if (saved == null) return null;
        }
        return new FileSystemResource(file);
    }

    /**
     * 지정한 날짜와 서버 타입에 해당하는 로그 파일의 존재 여부를 확인합니다.
     * 파일이 없으면 downloadAndSaveLog를 호출하여 다운로드 시도
     *
     * @param date       날짜 (YYYYMMDD 형식)
     * @param serverType 서버 타입 ("web" 또는 "deliveryapp")
     * @return 파일 존재 여부 (true: 존재, false: 없음 또는 다운로드 실패)
     */
    public boolean checkFileExists(String date, String serverType) {
        String localFolder = getLocalFolder(serverType);
        String fileName;
        if ("deliveryapp".equalsIgnoreCase(serverType)) {
            fileName = "deliveryapp-" + date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6) + ".log.gz";
        } else {
            fileName = "log_" + date + ".txt";
        }
        String filePath = localFolder + "/" + fileName;
        File file = new File(filePath);
        if (!file.exists()) {
            String saved = downloadAndSaveLog(date, serverType);
            return saved != null;
        }
        return true;
    }
}
