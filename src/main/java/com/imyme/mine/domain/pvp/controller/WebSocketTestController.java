package com.imyme.mine.domain.pvp.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class WebSocketTestController {

    @GetMapping(value = "/websocket-test", produces = MediaType.TEXT_HTML_VALUE)
    public String websocketTest() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/websocket-test.html");
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}