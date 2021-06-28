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
public class KvcNnLoginDto implements Serializable {

  private String captchaCode;
  private String captchaGuid;
  private String accountNumber;
  private String accountRegion;

  @Override
  public String toString() {
    return "{" +
        "captchaCode='" + captchaCode + '\'' +
        ", captchaGuid='" + captchaGuid + '\'' +
        ", accountNumber='" + accountNumber + '\'' +
        ", accountRegion='" + accountRegion + '\'' +
        '}';
  }
}
