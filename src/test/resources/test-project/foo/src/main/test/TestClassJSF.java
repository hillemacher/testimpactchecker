/**
 * Sample test fixture class used by resource-based impact tests.
 */
@ContextConfiguration
public class TestClassJSF {

  private InterfaceJSF a;

  void test() {
    InterfaceJSF b = new ClassJSF();
  }
}
