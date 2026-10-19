package {{packageName}};
{{annotations}}
public interface {{className}}{{extendsClause}} {
    /** 原子请求与响应契约；版本支持由实际框架选择。 */
    {{responseType}} {{operation}}({{requestType}} request);
}
