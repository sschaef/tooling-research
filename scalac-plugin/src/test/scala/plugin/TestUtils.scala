package plugin

import org.junit.ComparisonFailure

object TestUtils {
  final implicit class Assert_===[A](private val actual: A) extends AnyVal {
    def ===(expected: A): Unit = {
      if (actual != expected)
        throw new ComparisonFailure("", expected.toString, actual.toString)
    }
  }
}
