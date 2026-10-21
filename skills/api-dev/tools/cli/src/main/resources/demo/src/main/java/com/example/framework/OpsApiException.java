package com.example.framework;

public class OpsApiException extends Exception {
  public OpsApiException(String message) {
    super(message);
  }

  public String getErrMsg() {
    return getMessage();
  }
}
