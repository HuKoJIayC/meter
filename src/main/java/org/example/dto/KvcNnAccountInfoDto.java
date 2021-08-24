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
public class KvcNnAccountInfoDto implements Serializable {

  private String number;
  private String region;

  @Override
  public String toString() {
    return "{" +
        "number='" + number + '\'' +
        ", region='" + region + '\'' +
        '}';
  }
}
