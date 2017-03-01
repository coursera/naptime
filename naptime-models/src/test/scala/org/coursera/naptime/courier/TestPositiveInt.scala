package org.coursera.naptime.courier

import com.linkedin.data.template.Custom
import com.linkedin.data.template.DirectCoercer

case class TestPositiveInt(value: Int) extends AnyVal

object TestPositiveInt {
  object Coercer extends DirectCoercer[TestPositiveInt] {
    override def coerceInput(obj: TestPositiveInt): AnyRef = {
      Int.box(obj.value)
    }

    override def coerceOutput(obj: Any): TestPositiveInt = {
      obj match {
        case value: java.lang.Integer =>
          if (value > 0) {
            TestPositiveInt(value)
          } else {
            throw new IllegalArgumentException(s"$value is not positive")
          }
        case _: Any =>
          throw new IllegalArgumentException()
      }
    }

    def registerCoercer(): Unit = {
      Custom.registerCoercer(this, classOf[TestPositiveInt])
    }
  }

  Coercer.registerCoercer()
}
