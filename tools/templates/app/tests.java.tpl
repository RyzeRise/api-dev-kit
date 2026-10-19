/** App 业务测试待办：{{operation}}。请求 {{requestType}}，响应 {{responseType}}。
 * 按项目真实 JUnit 版本迁入相关模块 src/test/java；替换每个失败体为真实调用和断言。
 * 本文件不依赖测试框架，直接运行必失败，不能作为业务测试通过的证据。
 */
public class {{className}} {
    public static void main(String[] args) throws Exception {
        legalRequestUsesCorrectAtomicAndStages();
        invalidInputDoesNotCallAtomic();
        conversionPreservesFieldsDefaultsOrderAndNull();
        eachBoundaryFailurePreservesCause();
    }
    static void legalRequestUsesCorrectAtomicAndStages() throws Exception {
        // 待补：从 Handler.run 调用，捕获真实请求，断言 pre/invoke/post 与版本选择。
        throw new AssertionError("TEST_IMPLEMENTATION_REQUIRED: Handler lifecycle and real result");
    }
    static void invalidInputDoesNotCallAtomic() throws Exception {
        // 待补：非法输入与查无数据；断言底层调用次数及约定响应。
        throw new AssertionError("TEST_IMPLEMENTATION_REQUIRED: invalid input and no data");
    }
    static void conversionPreservesFieldsDefaultsOrderAndNull() throws Exception {
        // 待补：请求/响应 Converter、完整字段、请求回填、null/空集合、顺序及 wire 基线。
        throw new AssertionError("TEST_IMPLEMENTATION_REQUIRED: request and response compatibility");
    }
    static void eachBoundaryFailurePreservesCause() throws Exception {
        // 待补：Delegate 的请求转换、Facade、响应转换分别失败，断言 getErrMsg/cause。
        throw new AssertionError("TEST_IMPLEMENTATION_REQUIRED: exception boundaries");
    }
}
