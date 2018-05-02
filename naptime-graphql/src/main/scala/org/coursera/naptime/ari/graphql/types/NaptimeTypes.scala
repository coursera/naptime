package org.coursera.naptime.ari.graphql.types

import com.linkedin.data.DataMap
import com.linkedin.data.codec.JacksonDataCodec
import sangria.ast.StringValue
import sangria.schema.ScalarType
import sangria.validation.ValueCoercionViolation
import sangria.validation.Violation

import scala.util.Try

object NaptimeTypes {
  case object DataMapCoercionViolation extends ValueCoercionViolation("DataMap value expected")

  private[this] val dataCodec = new JacksonDataCodec()

  private[this] def stringToDataMapEither(string: String): Either[Violation, DataMap] = {
    Try(dataCodec.stringToMap(string)).toOption
      .map(Right(_))
      .getOrElse(Left(DataMapCoercionViolation))
  }

  val DataMapType = ScalarType[DataMap](
    "DataMap",
    description = Some("Pegasus DataMap, with an arbitrary JSON-like value"),
    coerceOutput = (value, _) ⇒ value,
    coerceUserInput = {
      case string: String => stringToDataMapEither(string)
      case _              => Left(DataMapCoercionViolation)
    },
    coerceInput = {
      case StringValue(string, _, _, _, _) => stringToDataMapEither(string)
      case _                               => Left(DataMapCoercionViolation)
    }
  )
}
