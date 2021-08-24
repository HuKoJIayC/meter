package org.example.services;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.example.utils.ConverterUtils.isNumber;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.KvcNnCaptchaDto;
import org.example.dto.KvcNnCounterDto;
import org.example.dto.KvcNnAccountInfoDto;
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
    url = new URL("https://kvc-nn.ru");
    userAgent =
        "Mozilla/5.0 (Windows NT 6.2; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/32.0.1667.0 "
            + "Safari/537.36";
    cookies = null;
    counters = null;
  }

  /**
   * Авторизация по номеру лицевого счёта и району с валидацией и вводом капчи
   *
   * @param account Информация о счёте
   * @param captcha Guid и код капчи
   * @return Успешность аутентификации
   */
  public boolean authorization(
      KvcNnAccountInfoDto account,
      KvcNnCaptchaDto captcha
  ) {
    log.info("Start of authorization");
    if (isNull(account)) {
      log.error("AccountInfo is null");
      return false;
    }
    if (isNull(account.getNumber()) || !isNumber(account.getNumber())) {
      log.error("PersonalAccountNumber {} is not numeric", account.getNumber());
      return false;
    }
    if (isNull(account.getRegion()) || !isNumber(account.getRegion())) {
      log.error("Region {} is not defined", account.getRegion());
      return false;
    }
    // Если информация о капче получена на вход - попытка только 1
    boolean isLoginSuccess;
    if (nonNull(captcha)) {
      isLoginSuccess = login(account, captcha);
      if (isLoginSuccess) {
        log.error("Invalid captcha information");
      } else {
        log.info("Authorization successful");
      }
      return isLoginSuccess;
    }
    // Если информация о капче не получена на вход - несколько попыток
    log.info("Entering captcha code");
    int captchaCountMaxAttempts = 5;
    for (int i = 1; i <= captchaCountMaxAttempts; i++) {
      log.debug("Attempt {} of {} to enter a captcha", i, captchaCountMaxAttempts);
      captcha = getCaptcha();
      isLoginSuccess = login(account, captcha);
      if (!isLoginSuccess) {
        log.warn("Invalid captcha number entered");
      } else {
        log.info("Authorization successful");
        return true;
      }
    }
    log.error("Max count of attempts to enter captcha");
    return false;
  }

  /**
   * Отправка показаний счётчика
   *
   * @param counterNumber Номер счётчика
   * @param value Значение
   * @return Результат отправки показаний
   */
  public boolean sendMeterData(String counterNumber, String value) {
    if (isNull(cookies)) {
      log.error("Cookies not found");
      return false;
    }

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
        //  Происследовать необходимость открытия счётчика для передачи показаний
        Response response;
        Document document;
        try {
          response =
              Jsoup.connect(url + counter.getLink()).cookies(cookies).execute();
          document = response.parse();
        } catch (IOException e) {
          log.error(e.getMessage());
          return false;
        }
        // TODO refactoring 1
        if (response.statusCode() != 200) {
          log.error("Get meter info status is not 200");
          return false;
        }
        Elements inputElements = document.select("input[name=csrfmiddlewaretoken]");
        if (inputElements.size() == 0) {
          log.error("csrfmiddlewaretoken is not found");
          return false;
        }
        String securityTokenKey = inputElements.get(0).attr("name");
        String securityTokenValue = inputElements.get(0).attr("value");

        Response responseMeter;
        Document documentMeter;
        try {
          responseMeter =
              Jsoup.connect(url + counter.getLink())
                  .followRedirects(false)
                  .userAgent(userAgent)
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .cookies(cookies)
                  .data(securityTokenKey, securityTokenValue)
                  .data("pick", value)
                  .method(Method.POST)
                  .execute();
          documentMeter = responseMeter.parse();
        } catch (IOException e) {
          log.error(e.getMessage());
          return false;
        }
        if (responseMeter.statusCode() != 200) {
          log.error("Get meter info status is not 200");
          return false;
        }

        // TODO end
        return documentMeter.select("div[class*=alert-success]").size() == 1;
      }
    }

    return false;
  }

  /**
   * Вывод диалогового окна с капчей и полем для заполнения кода
   *
   * @return Информиция о капче - ид и код
   */
  private KvcNnCaptchaDto getCaptcha() {
    final String guid = java.util.UUID.randomUUID().toString();
    final BufferedImage captchaImage = getBufferedImageCaptcha(guid); // TODO if null ???
    final String code =
        (String)
            JOptionPane.showInputDialog(
                null,
                "Enter captcha code:",
                "Captcha",
                JOptionPane.QUESTION_MESSAGE,
                new ImageIcon(captchaImage),
                null,
                null);
    if (isNull(code)) {
      log.error("Captcha code is no entered");
      return null;
    }
    return KvcNnCaptchaDto.builder()
        .code(code)
        .guid(guid)
        .build();
  }

  /**
   * Получение изображения с капчей
   *
   * @param guid Ид для генерации капчи
   * @return Изображение с капчей
   */
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

  /**
   * Авторизация на сайте
   *
   * @param account Информация о клиенте
   * @param captcha Информация о капче
   * @return Результат попытки авторизации
   */
  private boolean login(KvcNnAccountInfoDto account, KvcNnCaptchaDto captcha) {
    log.info("Welcome");
    Response welcomeResponse;
    Document welcomeDocument;
    try {
      welcomeResponse =
          Jsoup.connect(url + "/meter/")
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
    // TODO refactoring 1
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
    log.debug("KvcNnAccountInfoDto {}", account.toString());
    log.debug("KvcNnCaptchaDto {}", captcha.toString());
    Response loginResponse;
    try {
      loginResponse =
          Jsoup.connect(url + "/meter/")
              .followRedirects(false)
              .userAgent(userAgent)
              .header("Content-Type", "application/x-www-form-urlencoded")
              .cookies(cookies)
              .data(securityTokenKey, securityTokenValue)
              .data("select_region", account.getRegion())
              .data("input_lc", account.getNumber())
              .data("passctr", "None")
              .data("input_captcha", captcha.getCode())
              .data("input_guid", captcha.getGuid())
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
          Jsoup.connect(url + "/meter/cntlist/").cookies(cookies).execute();
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
