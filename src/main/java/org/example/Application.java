package org.example;

import org.example.services.KvcNnService;

public class Application {

  public static void main(String[] args) {

    KvcNnService kvcNnService = new KvcNnService();
    if (kvcNnService.authorization(args[0], args[1])) {
      kvcNnService.sendMeterData();
    }



  }
}
