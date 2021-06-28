package org.example.services;

import static java.util.Objects.isNull;
import static org.example.utils.ConverterUtils.isNumber;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.KvcNnCounterDto;
import org.example.dto.KvcNnLoginDto;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@Slf4j
public class KvcNnService {

  private final URL url;
  private final String userAgent;

  private Map<String, String> cookies;
  private List<KvcNnCounterDto> counters;

  @SneakyThrows
  public KvcNnService() {
    log.info("Initialization service KvcNnService");
    url = new URL("https://kvc-nn.ru/");
    userAgent =
        "Mozilla/5.0 (Windows NT 6.2; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/32.0.1667.0 "
            + "Safari/537.36";
    cookies = null;
    counters = null;
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

    log.info("Entering captcha code");
    int captchaCounter = 5;
    boolean isLoginSuccess;
    do {
      log.debug("Number of attempts to enter captcha {}", captchaCounter);
      final String guid = java.util.UUID.randomUUID().toString();
      final BufferedImage captchaImage = getBufferedImageCaptcha(guid); // TODO if null ???
      final String captchaCode =
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

  public boolean sendMeterData(String counterNumber, String value) {
    if (isNull(counters)) {
      counters = getCounters();
      if (counters.size() == 0) {
        log.error("Counters not found");
        return false;
      }
    }

    for (KvcNnCounterDto counter : counters) {
      if (counter.getName().contains(counterNumber)) {

        // TODO Передача показаний

        return true;
      }
    }

    return false;
  }

  private BufferedImage getBufferedImageCaptcha(String guid) {
    String urlString =
        url.getProtocol() + "://captcha." + url.getHost() + "/?uuid=" + guid + "&l=1";
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

    log.info("Welcome");
    Response welcomeResponse;
    Document welcomeDocument;
    try {
      welcomeResponse =
          Jsoup.connect(url + "meter/")
              .userAgent(userAgent)
              .timeout(10000)
              .method(Method.GET)
              .execute();
      welcomeDocument = welcomeResponse.parse();
      cookies = welcomeResponse.cookies();
    } catch (IOException e) {
      log.error(e.getMessage());
      return false;
    }

    if (welcomeResponse.statusCode() != 200) {
      log.error("Welcome page status is not 200");
      return false;
    }

    Elements inputElements = welcomeDocument.select("input[name=csrfmiddlewaretoken]");
    if (inputElements.size() == 0) {
      log.error("csrfmiddlewaretoken is not found");
      return false;
    }
    String securityTokenKey = inputElements.get(0).attr("name");
    String securityTokenValue = inputElements.get(0).attr("value");

    log.info("Login");
    log.debug("KvcNnLoginDto {}", loginDto.toString());

    Response loginResponse;
    try {
      loginResponse =
          Jsoup.connect(url + "meter/")
              .followRedirects(false)
              .userAgent(userAgent)
              .header("Content-Type", "application/x-www-form-urlencoded")
              .cookies(cookies)
              .data(securityTokenKey, securityTokenValue)
              .data("select_region", loginDto.getAccountRegion())
              .data("input_lc", loginDto.getAccountNumber())
              .data("passctr", "None")
              .data("input_captcha", loginDto.getCaptchaCode())
              .data("input_guid", loginDto.getCaptchaGuid())
              .method(Method.POST)
              .execute();
    } catch (IOException e) {
      log.error(e.getMessage());
      return false;
    }

    if (loginResponse.statusCode() != 302) {
      log.error("Login page status is not 302");
      return false;
    }

    cookies.putAll(loginResponse.cookies());
    return true;
  }

  private List<KvcNnCounterDto> getCounters() {
    log.info("Getting counters");
    List<KvcNnCounterDto> counters = new ArrayList<>();

    Response response;
    Document document;
    try {
      response =
          Jsoup.connect(url + "meter/cntlist/").cookies(cookies).execute();
      document = response.parse();
    } catch (IOException e) {
      log.error(e.getMessage());
      return counters;
    }

    if (response.statusCode() != 200) {
      log.error("CounterList page status is not 200");
      return counters;
    }

    Element countersTable = document.getElementById("meter-cntlist-table");
    if (isNull(countersTable)) {
      log.error("CounterList table not found");
      return counters;
    }

    Elements trElements = countersTable.getElementsByTag("tr");
    for (Element trElement : trElements) {
      Elements tdElements = trElement.getElementsByTag("td");
      if (tdElements.size() < 2) {
        continue;
      }

      KvcNnCounterDto counterDto =
          KvcNnCounterDto.builder()
              .link(tdElements.get(0).select("a").attr("href").trim())
              .name(tdElements.get(0).select("a").text())
              .info(tdElements.get(1).text())
              .build();
      counters.add(counterDto);
    }

    log.info("Found {} counters", counters.size());
    return counters;
  }
}
