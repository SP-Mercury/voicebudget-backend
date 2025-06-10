package com.voicebudget.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")  // 允許的路徑，這裡設定為所有路徑都可以訪問
        .allowedOrigins("*")  // 允許所有來源
        .allowedMethods("GET", "POST", "PUT", "DELETE")  // 設置允許的 HTTP 方法
        .allowedHeaders("*");  // 允許的標頭
}
}
