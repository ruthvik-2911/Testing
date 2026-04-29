package org.jackfruit.keliri.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve local uploads from /api/uploads/**
        String projectRoot = System.getProperty("user.dir");
        Path uploadDir = Paths.get(projectRoot, "uploads");
        String uploadPath = uploadDir.toFile().getAbsolutePath();

        registry.addResourceHandler("/api/uploads/**")
                .addResourceLocations("file:/" + uploadPath + "/");
    }
}
