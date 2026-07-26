package org.phuchoang2005.management;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {
  @GetMapping("/error")
  public ResponseEntity<String> getError() {
    return new ResponseEntity<>("There is no error for this", HttpStatusCode.valueOf(200));
  }
}
