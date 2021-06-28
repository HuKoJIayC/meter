package org.example.utils;

import java.util.Objects;

public class ConverterUtils {

  /**
   * Проверка что объект является числом
   *
   * @param obj Объект
   * @return Результат проверки
   */
  public static boolean isNumber(final Object obj) {
    if (obj != null) {
      final Class<?> c = obj.getClass();
      return c == Integer.class
          || c == Double.class
          || c == Float.class
          || c == Short.class
          || c == Byte.class;
    }
    return false;
  }

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
