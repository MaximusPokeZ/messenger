package org.example.frontend.model.main;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatSetting {
  private String cipher;
  private String cipherMode;
  private String paddingMode;
  private String iv;
  private String keyBitLength;
}
