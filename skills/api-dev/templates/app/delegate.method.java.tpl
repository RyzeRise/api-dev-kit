{{overrideAnnotation}}public {{externalResponse}} {{operation}}({{parameters}}){{throwsClause}} {
    try {
        // 1. 转换业务请求
        {{requestType}} {{requestVariable}} = {{requestConverter}}.INSTANCE.{{requestMethod}}({{arguments}});
        // 2. 调用业务 Facade
        {{facadeType}} {{facadeVariable}} = this.{{managerVariable}}.getFacadeImpl({{facadeType}}.class);
        {{responseType}} {{resultVariable}} = {{facadeVariable}}.{{operation}}({{requestVariable}});
        // 3. 只做响应映射，转换异常保留在同一边界内。
        return {{responseCall}}({{resultVariable}});
    } catch ({{internalException}} {{errorVariable}}) {
        {{failureAdaptation}}
    }
}
