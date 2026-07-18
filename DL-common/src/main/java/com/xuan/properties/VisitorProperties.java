package com.xuan.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dl.visitor")
@Data
public class VisitorProperties {
    private String verifyCode;
}
