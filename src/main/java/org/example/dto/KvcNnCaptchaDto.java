package org.example.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KvcNnCaptchaDto implements Serializable {

  private String code;
  private String guid;

  @Override
  public String toString() {
    return "{" +
        "code='" + code + '\'' +
        ", guid='" + guid + '\'' +
        '}';
  }
}
