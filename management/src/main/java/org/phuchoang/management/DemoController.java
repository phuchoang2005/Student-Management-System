package org.phuchoang.management;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {
  @RequestMapping("/home")
  public ResponseEntity<String> getError() {
    return new ResponseEntity<>("There is no error for this", HttpStatusCode.valueOf(200));
  }
}
