package com.aivora.auth.controller;

import com.aivora.auth.service.GuestService;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/guest/session")
public class GuestController {

  private final GuestService guests;

  public GuestController(GuestService guests) {
    this.guests = guests;
  }

  @GetMapping
  public Object status(HttpServletRequest req) {
    return guests.status(req);
  }

  @PostMapping
  public Object start(HttpServletRequest req, HttpServletResponse res) {
    return guests.start(req, res);
  }
}
