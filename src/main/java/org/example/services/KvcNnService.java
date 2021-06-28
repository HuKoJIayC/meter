package org.example.services;

import static java.util.Objects.isNull;
import static org.example.utils.ConverterUtils.isNumber;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.KvcNnLoginDto;

@Slf4j
public class KvcNnService {

  private final HttpClient client;
  private final String hostName;

  private String csrfMiddlewareToken;
  private String csrfToken;
  private String sessionMeter;

  public KvcNnService() {
    log.info("Initialization service KvcNnService");
    hostName = "kvc-nn.ru";
    client = HttpClient.newHttpClient();
    csrfMiddlewareToken = null;
    csrfToken = null;
    sessionMeter = null;
  }

  public boolean authorization(String personalAccountNumber, String region) {
    log.info("Start of authorization");

    if (isNull(personalAccountNumber) || !isNumber(personalAccountNumber)) {
      log.error("PersonalAccountNumber {} is not numeric", personalAccountNumber);
      return false;
    }

    if (isNull(region) || !isNumber(region)) {
      log.error("Region {} is not defined", region);
      return false;
    }

    if (!getCsrfInfo()) {
      log.error("CsrfInfo is not found");
      return false;
    }

    log.info("Entering captcha code");
    int captchaCounter = 5;
    boolean isLoginSuccess;
    do {
      log.debug("Number of attempts to enter captcha {}", captchaCounter);
      String guid = java.util.UUID.randomUUID().toString();
      BufferedImage captchaImage = getBufferedImageCaptcha(guid);
      String captchaCode =
          (String)
              JOptionPane.showInputDialog(
                  null,
                  "Enter captcha code:",
                  "Captcha",
                  JOptionPane.QUESTION_MESSAGE,
                  new ImageIcon(captchaImage),
                  null,
                  null);
      if (isNull(captchaCode)) {
        log.error("Captcha code is no entered");
        return false;
      }

      final KvcNnLoginDto loginDto =
          KvcNnLoginDto.builder()
              .csrfMiddlewareToken(csrfMiddlewareToken)
              .captchaCode(captchaCode)
              .captchaGuid(guid)
              .accountNumber(personalAccountNumber)
              .accountRegion(region)
              .build();
      isLoginSuccess = login(loginDto);
      if (!isLoginSuccess) {
        log.warn("Invalid captcha number entered");
      }

      if (captchaCounter-- == 0) {
        log.error("Max count of attempts to enter captcha");
        return false;
      }

    } while (!isLoginSuccess);

    log.info("Authorization successful");
    return true;
  }

  public boolean sendMeterData() {

    // TODO Должен быть получен список счётчиков
    getCntList();
    // TODO Передача показаний

    return true;
  }

  private boolean getCsrfInfo() {
    log.info("Getting CsrfInfo");

    final HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://" + hostName + "/meter/"))
        .build();

    HttpResponse<String> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException | InterruptedException e) {
      log.error(e.getMessage());
      return false;
    }

    if (isNull(response) || response.statusCode() != 200) {
      log.error("Response was not received or status is not 200");
      return false;
    }

    String cookie = response.headers().firstValue("Set-Cookie").orElse(null);
    if (isNull(cookie) || !cookie.contains("csrftoken")) {
      log.error("Cookie was not received or csrftoken is not found");
      return false;
    }
    csrfToken = cookie.substring(0, cookie.indexOf(";"));

    if (!response.body().contains("name='csrfmiddlewaretoken' value='")) {
      log.error("csrfmiddlewaretoken is not found");
      return false;
    }

    int beginIndexResponse = response.body().indexOf("name='csrfmiddlewaretoken' value='") + 34;
    csrfMiddlewareToken = response.body().substring(beginIndexResponse, beginIndexResponse + 64);
    return true;
  }

  private BufferedImage getBufferedImageCaptcha(String guid) {
    String urlString = "https://captcha." + hostName + "/?uuid=" + guid + "&l=1";
    BufferedImage image = null;
    try {
      URL url = new URL(urlString);
      image = ImageIO.read(url);
    } catch (IOException e) {
      log.error(e.getMessage());
    }
    return image;
  }

  private boolean login(KvcNnLoginDto loginDto) {
    log.info("Login");
    log.debug("KvcNnLoginDto {}", loginDto.toString());
    HttpRequest httpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create("https://" + hostName + "/meter/"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Cookie", "csrftoken=" + csrfToken)
            .POST(
                BodyPublishers.ofString(
                    "csrfmiddlewaretoken="
                        + loginDto.getCsrfMiddlewareToken()
                        + "&"
                        + "select_region="
                        + loginDto.getAccountRegion()
                        + "&"
                        + "input_lc="
                        + loginDto.getAccountNumber()
                        + "&"
                        + "passctr=None&"
                        + "input_captcha="
                        + loginDto.getCaptchaCode()
                        + "&"
                        + "input_guid="
                        + loginDto.getCaptchaGuid()))
            .build();

    HttpResponse<String> response;
    try {
      response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    } catch (IOException | InterruptedException e) {
      log.error(e.getMessage());
      return false;
    }

    if (isNull(response) || response.statusCode() != 302) {
      log.error("Response was not received or status is not 302");
      return false;
    }

    String cookie = response.headers().firstValue("Set-Cookie").orElse(null);
    if (isNull(cookie) || !cookie.contains("session_meter")) {
      log.error("Cookie was not received or session_meter is not found");
      return false;
    }
    sessionMeter = cookie.substring(0, cookie.indexOf(";"));

    return true;
  }

  private void getCntList() {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("https://" + hostName + "/meter/cntlist/"))
            .header("Cookie", csrfToken + "; " + sessionMeter)
            .build();

    HttpResponse<String> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
      System.out.println();
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
      return;
    }
  }
}
