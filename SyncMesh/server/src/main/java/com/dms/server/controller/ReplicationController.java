package com.dms.server.controller;

import com.dms.common.model.Message;
import com.dms.server.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class ReplicationController {
    @Autowired
    private MessageService messageService;

    @PostMapping("/replica")
    public Message replicate(@RequestBody Message m) {
        return messageService.acceptReplica(m);
    }
}
