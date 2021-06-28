package org.example.utils;

import java.util.Objects;

public class ConverterUtils {

  /**
   * Проверка что значение строки является числом
   *
   * @param value Строка
   * @return Результат проверки
   */
  public static boolean isNumber(String value) {
    if (Objects.isNull(value)) {
      return false;
    }

    try {
      Long.parseLong(value);
    } catch (Exception e) {
      return false;
    }

    return true;
  }
}
