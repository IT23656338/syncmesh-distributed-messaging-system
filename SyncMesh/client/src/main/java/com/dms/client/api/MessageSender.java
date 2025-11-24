package com.dms.client.api;

import com.dms.common.model.Message;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class MessageSender {
    private final RestTemplate rest = new RestTemplate();

    public Message send(String baseUrl, Message m) {
        String url = baseUrl + "/api/messages/send";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Message> req = new HttpEntity<>(m, headers);
        ResponseEntity<Message> resp = rest.postForEntity(url, req, Message.class);
        return resp.getBody();
    }
}
