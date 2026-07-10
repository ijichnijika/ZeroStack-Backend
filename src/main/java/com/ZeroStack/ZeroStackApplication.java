package com.ZeroStack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class ZeroStackApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZeroStackApplication.class, args);
    }

}
