package com.samuel.app.creator.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    @GetMapping("/testDenied")
    public String denied() {
        return "testDenied";
    }

    @GetMapping("/testPermit")
    public String permit() {
        return "testPermit";
    }
}
