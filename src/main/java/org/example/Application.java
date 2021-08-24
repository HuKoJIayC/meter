package org.example;

import org.example.dto.KvcNnAccountInfoDto;
import org.example.services.KvcNnService;

public class Application {

  public static void main(String[] args) {

    KvcNnService kvcNnService = new KvcNnService();
    if (kvcNnService.authorization(
        KvcNnAccountInfoDto.builder().number(args[0]).region(args[1]).build(), null
    )) {
      kvcNnService.sendMeterData(args[2], args[3]);
    }
  }
}
