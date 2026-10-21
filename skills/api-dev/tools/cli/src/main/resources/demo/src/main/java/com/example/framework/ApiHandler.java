package com.example.framework;

public abstract class ApiHandler<I, O> {
  protected static class BasicApiContext {}

  protected abstract BasicApiContext preHandler(I request) throws OpsApiException;

  protected abstract BasicApiContext invokeAtomicApi(BasicApiContext context)
      throws OpsApiException;

  protected abstract O postHandler(BasicApiContext context) throws OpsApiException;

  public O run(I request) throws OpsApiException {
    return postHandler(invokeAtomicApi(preHandler(request)));
  }
}
