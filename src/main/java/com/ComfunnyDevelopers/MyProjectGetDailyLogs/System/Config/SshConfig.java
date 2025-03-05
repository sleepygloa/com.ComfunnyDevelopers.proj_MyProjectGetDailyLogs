package com.ComfunnyDevelopers.MyProjectGetDailyLogs.System.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ssh")
public class SshConfig {
    private String host;
    private int port;
    private String user;
    private String password;
    // 웹 서버와 배송기사앱의 로그 경로를 별도 설정
    private String webLogPath;
    private String deliveryappLogPath;
}