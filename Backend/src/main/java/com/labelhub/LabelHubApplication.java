package com.labelhub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.labelhub.modules.*.mapper")
public class LabelHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabelHubApplication.class, args);
    }
}
