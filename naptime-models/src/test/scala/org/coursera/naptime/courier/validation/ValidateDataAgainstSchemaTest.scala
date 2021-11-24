package org.coursera.naptime.courier.validation

import java.io.ByteArrayInputStream

import com.linkedin.data.ByteString
import com.linkedin.data.Data
import com.linkedin.data.DataComplex
import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.codec.JacksonDataCodec
import com.linkedin.data.element.DataElement
import com.linkedin.data.element.DataElementUtil
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.SchemaParser
import com.linkedin.data.schema.validation.ValidationResult
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class ValidateDataAgainstSchemaTest extends AssertionsForJUnit {
  import ValidateDataAgainstSchemaTest._

  def testCoercionValidation(
      schemaText: String,
      key: String,
      inputs: List[(AnyRef, AnyRef)],
      badObjects: List[AnyRef],
      coercionMode: CoercionMode): Unit = {
    val options = normalCoercionValidationOption.copy(coercionMode = coercionMode)
    val schema = dataSchemaFromString(schemaText).asInstanceOf[RecordDataSchema]
    assert(schema !== null)
    val map = new DataMap
    for (row <- inputs) {
      map.put(key, row._1)
      val result = validate(map, schema, options)
      assert(result.isValid)
      if (result.hasFix) {
        val fixedMap = result.getFixed.asInstanceOf[DataMap]
        assert(fixedMap.getClass eq classOf[DataMap])
        val fixed = fixedMap.get(key)
        assert(fixed !== null)
        val fixedClass = fixed.getClass
        val goodClass = row._1.getClass
        schema.getField(key).getType.getDereferencedType match {
          case DataSchema.Type.BYTES | DataSchema.Type.FIXED =>
            // String to ByteString conversion check
            assert(goodClass ne fixedClass)
            assert(goodClass eq classOf[java.lang.String])
            assert(fixedClass eq classOf[ByteString])
            assert(fixed.asInstanceOf[ByteString].asAvroString === row._1)

          case DataSchema.Type.INT =>
            // convert numbers to Integer
            assert(goodClass ne fixedClass)
            assertAllowedClass(coercionMode, goodClass)
            assert(fixedClass eq classOf[java.lang.Integer])

          case DataSchema.Type.LONG =>
            // convert numbers to Long
            assert(goodClass ne fixedClass)
            assertAllowedClass(coercionMode, goodClass)
            assert(fixedClass eq classOf[java.lang.Long])

          case DataSchema.Type.FLOAT =>
            // convert numbers to Float
            assert(goodClass ne fixedClass)
            assertAllowedClass(coercionMode, goodClass)
            assert(fixedClass eq classOf[java.lang.Float])

          case DataSchema.Type.DOUBLE =>
            // convert numbers to Double
            assert(goodClass ne fixedClass)
            assertAllowedClass(coercionMode, goodClass)
            assert(fixedClass eq classOf[java.lang.Double])

          case DataSchema.Type.BOOLEAN =>
            if (coercionMode === CoercionMode.STRING_TO_PRIMITIVE) {
              assert(goodClass ne fixedClass)
              assert(goodClass === classOf[java.lang.String])
              assert(fixedClass eq classOf[java.lang.Boolean])
            }

          case DataSchema.Type.RECORD | DataSchema.Type.ARRAY | DataSchema.Type.MAP |
              DataSchema.Type.UNION =>
            assert(goodClass eq fixedClass)

          case _ =>
            throw new IllegalStateException("unknown conversion")
        }
        assert(fixed === row._2)
      } else assert(map eq result.getFixed)
    }
    for (bad <- badObjects) {
      map.put(key, bad)
      val result = validate(map, schema, options)
      assert(!result.isValid)
      assert(map eq result.getFixed)
    }
  }

  // Tests for CoercionMode.NORMAL
  def testNormalCoercionValidation(
      schemaText: String,
      key: String,
      inputs: List[(AnyRef, AnyRef)],
      badObjects: List[AnyRef]): Unit =
    testCoercionValidation(schemaText, key, inputs, badObjects, CoercionMode.NORMAL)

  // Tests for CoercionMode.STRING_TO_PRIMITIVE
  def testStringToPrimitiveCoercionValidation(
      schemaText: String,
      key: String,
      inputs: List[(AnyRef, AnyRef)],
      badObjects: List[AnyRef]): Unit =
    testCoercionValidation(schemaText, key, inputs, badObjects, CoercionMode.STRING_TO_PRIMITIVE)

  def testCoercionValidation(
      schemaText: String,
      key: String,
      goodObjects: Seq[AnyRef],
      badObjects: Seq[AnyRef],
      options: ValidationOptions): Unit = {
    val schema = dataSchemaFromString(schemaText).asInstanceOf[RecordDataSchema]
    assert(schema != null)
    val map = new DataMap
    for (good <- goodObjects) {
      map.put(key, good)
      val result = validate(map, schema, options)
      assert(result.isValid)
      assert(!result.hasFix)
      assert(map eq result.getFixed)
    }
    for (bad <- badObjects) {
      map.put(key, bad)
      val result = validate(map, schema, options)
      assert(!result.isValid)
      assert(map eq result.getFixed)
    }
  }

  @Test
  def testStringValidation(): Unit = {
    val goodObjects = List("a valid string")
    val badObjects = List(
      FALSE,
      I1,
      L1,
      F1,
      D1,
      ByteString.copyAvroString("bytes", false),
      new DataMap,
      new DataList)
    // There is no coercion for this type.
    // Test with all coercion modes, result should be the same for all cases.
    testCoercionValidation(
      STRING_SCHEMA,
      "bar",
      goodObjects,
      badObjects,
      normalCoercionValidationOption)
    testCoercionValidation(
      STRING_SCHEMA,
      "bar",
      goodObjects,
      badObjects,
      stringToPrimitiveCoercionValidationOption)
  }

  @Test
  def testBooleanValidation(): Unit = {
    val goodObjects = List(TRUE, FALSE)
    val badObjects = Array(
      I1,
      L1,
      F1,
      D1,
      new String("abc"),
      ByteString.copyAvroString("bytes", false),
      new DataMap,
      new DataList)
    testCoercionValidation(
      BOOLEAN_SCHEMA,
      "bar",
      goodObjects,
      badObjects,
      normalCoercionValidationOption)
  }

  @Test
  def testBooleanStringToPrimitiveFixupValidation(): Unit = {
    val input = List(
      (new String("true"), java.lang.Boolean.TRUE),
      (new String("false"), java.lang.Boolean.FALSE))
    val badObjects = List(I1, L1, F1, D1, new String("abc"), new DataMap, new DataList)
    testStringToPrimitiveCoercionValidation(BOOLEAN_SCHEMA, "bar", input, badObjects)
  }

  @Test
  def testIntegerNormalCoercionValidation(): Unit = {
    val input = List(
      (I1, I1),
      (new java.lang.Integer(-1), new java.lang.Integer(-1)),
      (
        new java.lang.Integer(java.lang.Integer.MAX_VALUE),
        new java.lang.Integer(java.lang.Integer.MAX_VALUE)),
      (
        new java.lang.Integer(java.lang.Integer.MAX_VALUE - 1),
        new java.lang.Integer(java.lang.Integer.MAX_VALUE - 1)),
      (new java.lang.Long(1), new Integer(1)),
      (new java.lang.Float(1), new Integer(1)),
      (new java.lang.Double(1), new Integer(1))
    )

    val badObjects = List(
      TRUE,
      new java.lang.String("abc"),
      ByteString.copyAvroString("bytes", false),
      new DataMap,
      new DataList)
    testNormalCoercionValidation(INTEGER_SCHEMA, "bar", input, badObjects)
  }

  @Test
  def testIntegerStringToPrimitiveCoercionValidation(): Unit = {
    val input = List(
      (new java.lang.String("1"), new java.lang.Integer(1)),
      (new java.lang.String("-1"), new java.lang.Integer(-1)),
      (new java.lang.String("" + Integer.MAX_VALUE), new java.lang.Integer(Integer.MAX_VALUE)),
      (
        new java.lang.String("" + (Integer.MAX_VALUE - 1)),
        new java.lang.Integer(Integer.MAX_VALUE - 1)),
      (new java.lang.String("1.5"), new java.lang.Integer(1)),
      (new java.lang.String("-1.5"), new java.lang.Integer(-1)),
      (new java.lang.Integer(1), new java.lang.Integer(1)),
      (new java.lang.Integer(-1), new java.lang.Integer(-1)),
      (new java.lang.Integer(Integer.MAX_VALUE), new java.lang.Integer(Integer.MAX_VALUE)),
      (
        new java.lang.Integer(Integer.MAX_VALUE - 1),
        new java.lang.Integer(
          Integer.MAX_VALUE -
            1)),
      (new java.lang.Long(1), new java.lang.Integer(1)),
      (new java.lang.Float(1), new java.lang.Integer(1)),
      (new java.lang.Double(1), new java.lang.Integer(1)))
    val badObjects = List(
      new java.lang.Boolean(true),
      new java.lang.String("abc"),
      ByteString.copyAvroString("bytes", false),
      new DataMap,
      new DataList)
    testStringToPrimitiveCoercionValidation(INTEGER_SCHEMA, "bar", input, badObjects)
  }

  @Test
  def testLongNormalCoercionValidation(): Unit = {
    val schemaText =
      """{ "type" : "record", "name" : "foo", "fields" :
        |[ { "name" : "bar", "type" : "long" }]}""".stripMargin
    testNormalCoercionValidation(schemaText, "bar", LONG_TEST_INPUTS, BAD_OBJECTS_FOR_NUMERIC)
  }

  @Test
  def testLongStringToPrimitiveCoercionValidation(): Unit = {
    val schemaText =
      """{ "type" : "record", "name" : "foo", "fields" :
        |[ { "name" : "bar", "type" : "long" } ] }""".stripMargin
    val inputs = List(
      (new java.lang.String("1"), new java.lang.Long(1)),
      (new java.lang.String("-1"), new java.lang.Long(-1)),
      (new java.lang.String("" + Long.MaxValue), new java.lang.Long(Long.MaxValue))) ++
      LONG_TEST_INPUTS
    testStringToPrimitiveCoercionValidation(schemaText, "bar", inputs, BAD_OBJECTS_FOR_NUMERIC)
  }

  @Test
  def testFloatNormalCoercionValidation(): Unit = {
    val schemaText =
      """{ "type" : "record", "name" : "foo", "fields" :
        |[ { "name" : "bar", "type" : "float" } ] }""".stripMargin
    testNormalCoercionValidation(schemaText, "bar", FLOAT_TEST_INPUTS, BAD_OBJECTS_FOR_NUMERIC)
  }

  @Test
  def testFloatStringToPrimitiveCoercionValidation(): Unit = {
    val schemaText =
      """{ "type" : "record", "name" : "foo", "fields" :
        |[ { "name" : "bar", "type" : "float" } ] }""".stripMargin
    val inputs = List(
      (new java.lang.String("1"), new java.lang.Float(1)),
      (new java.lang.String("-1"), new java.lang.Float(-1)),
      (new java.lang.String("1.01"), new java.lang.Float(1.01)),
      (new java.lang.String("-1.01"), new java.lang.Float(-1.01)),
      (new java.lang.String("" + Float.MaxValue), new java.lang.Float(Float.MaxValue))) ++
      FLOAT_TEST_INPUTS
    testStringToPrimitiveCoercionValidation(schemaText, "bar", inputs, BAD_OBJECTS_FOR_NUMERIC)
  }

  @Test
  def testDoubleNormalCoercionValidation(): Unit = {
    val schemaText =
      """{ "type" : "record", "name" : "foo", "fields" :
        |[ { "name" : "bar", "type" : "double" } ] }""".stripMargin
    testNormalCoercionValidation(schemaText, "bar", DOUBLE_TEST_INPUTS, BAD_OBJECTS_FOR_NUMERIC)
  }

  @Test
  def testDoubleStringToPrimitiveCoercionValidation(): Unit = {
    val schemaText =
      """{ "type" : "record", "name" : "foo", "fields" :
        |[ { "name" : "bar", "type" : "double" } ] }""".stripMargin
    val inputs = List(
      (new java.lang.String("1"), new java.lang.Double(1)),
      (new java.lang.String("-1"), new java.lang.Double(-1)),
      (new java.lang.String("1.01"), new java.lang.Double(1.01)),
      (new java.lang.String("-1.01"), new java.lang.Double(-1.01)),
      (new java.lang.String("" + Double.MaxValue), new java.lang.Double(Double.MaxValue))) ++
      DOUBLE_TEST_INPUTS
    testStringToPrimitiveCoercionValidation(schemaText, "bar", inputs, BAD_OBJECTS_FOR_NUMERIC)
  }

  @Test
  def testBytesValidation(): Unit = {
    val schemaText =
      """{ "type" : "record", "name" : "foo", "fields" :
        |[ { "name" : "bar", "type" : "bytes" } ] }""".stripMargin
    val badObjects = List(
      new java.lang.Boolean(true),
      new java.lang.Integer(1),
      new java.lang.Long(1),
      new java.lang.Float(1),
      new java.lang.Double(1),
      new DataMap,
      new DataList,
      new java.lang.String("\u0100"),
      new java.lang.String("ab\u0100c"),
      new java.lang.String("ab\u0100c\u0200"))
    val inputs = List(
      (ByteString.copyAvroString("abc", false), ByteString.copyAvroString("abc", false)),
      ("abc", ByteString.copyAvroString("abc", false)))
    testNormalCoercionValidation(schemaText, "bar", inputs, badObjects)
    testStringToPrimitiveCoercionValidation(schemaText, "bar", inputs, badObjects)
  }

  @Test
  def testFixedValidation(): Unit = {
    val schemaText =
      """{ "type" : "record", "name" : "foo", "fields" :
        |[ { "name" : "bar", "type" : { "name" : "fixed4", "type" : "fixed", "size" : 4 } } ] }
        |""".stripMargin
    val badObjects = List(
      new java.lang.Boolean(true),
      new java.lang.Integer(1),
      new java.lang.Long(1),
      new java.lang.Float(1),
      new java.lang.Double(1),
      new DataMap,
      new DataList,
      new java.lang.String,
      "1",
      "12",
      "123",
      "12345",
      "\u0100",
      "ab\u0100c",
      "b\u0100c\u0200",
      ByteString.empty,
      ByteString.copyAvroString("1", false),
      ByteString.copyAvroString("12", false),
      ByteString.copyAvroString("123", false),
      ByteString.copyAvroString("12345", false))
    val inputs = List(
      ("abcd", ByteString.copyAvroString("abcd", false)),
      ("\u0001\u0002\u0003\u0004", ByteString.copyAvroString("\u0001\u0002\u0003\u0004", false)),
      (ByteString.copyAvroString("abcd", false), ByteString.copyAvroString("abcd", false)),
      (
        ByteString.copyAvroString("\u0001\u0002\u0003\u0004", false),
        ByteString.copyAvroString("\u0001\u0002\u0003\u0004", false)))
    testNormalCoercionValidation(schemaText, "bar", inputs, badObjects)
    testStringToPrimitiveCoercionValidation(schemaText, "bar", inputs, badObjects)
  }

  @Test
  def testEnumCoercionValidation(): Unit = {
    val schemaText =
      """{ "type": "record", "name": "foo", "fields":
        |[ { "name": "bar",
        |    "type": { "name" : "fruits",
        |              "type" : "enum",
        |              "symbols" : [ "apple", "orange", "banana" ] }
        |      } ] }""".stripMargin
    val goodObjects = List(
      new java.lang.String("apple"),
      new java.lang.String("orange"),
      new java.lang.String("banana"))
    // There are no strings in the list of bad objects because all strings are accepted as valid
    // enum values, regardless of schema. They are deserialized to $UNKNOWN.
    val badObjects = List(
      new java.lang.Boolean(true),
      new java.lang.Integer(1),
      new java.lang.Long(1),
      new java.lang.Float(1),
      new java.lang.Double(1),
      new DataMap,
      new DataList)
    // There is no coercion for this type.
    // Test with all coercion validation options, result should be the same for all cases.
    testCoercionValidation(
      schemaText,
      "bar",
      goodObjects,
      badObjects,
      normalCoercionValidationOption)
    testCoercionValidation(
      schemaText,
      "bar",
      goodObjects,
      badObjects,
      stringToPrimitiveCoercionValidationOption)
  }

  @Test
  def testArrayNormalCoercionValidation(): Unit = {
    val schemaText =
      """{ "type" : "record", "name" : "foo", "fields" :
        | [ { "name" : "bar", "type" : { "type" : "array", "items" : "int" } } ] }""".stripMargin
    val badObjects = List(new DataList(asList(new String("1")))) ++ ARRAY_BAD_OBJECTS
    testNormalCoercionValidation(schemaText, "bar", ARRAY_TEST_INPUTS, badObjects)
  }

  @Test
  def testArrayStringToPrimitiveCoercionValidation(): Unit = {
    val schemaText =
      """{ "type" : "record", "name" : "foo", "fields" :
        |[ { "name" : "bar", "type" : { "type" : "array", "items" : "int" } } ] }""".stripMargin
    val inputs = List(
      (new DataList(asList("1")), new DataList(asList(1))),
      (new DataList(asList("1", "2", "3")), new DataList(asList(1, 2, 3)))) ++
      ARRAY_TEST_INPUTS
    testStringToPrimitiveCoercionValidation(schemaText, "bar", inputs, ARRAY_BAD_OBJECTS)
  }

  @Test
  def testMapNormalCoercionValidation(): Unit = {
    val schemaText =
      """{ "type" : "record", "name" : "foo", "fields" :
        |[ { "name" : "bar", "type" : { "type" : "map", "values" : "int" } } ] }""".stripMargin
    val badObjects = MAP_BAD_OBJECTS ++
      List(new DataMap(asMap("key1" -> new java.lang.String("1"))))
    testNormalCoercionValidation(schemaText, "bar", MAP_TEST_INPUTS, badObjects)
  }

  @Test
  def testMapStringToPrimitiveValidation(): Unit = {
    val schemaText =
      """{ "type" : "record", "name" : "foo", "fields" :
        |[ { "name" : "bar", "type" : { "type" : "map", "values" : "int" } } ] }""".stripMargin
    val inputs = List(
      (new DataMap(asMap("key1" -> "1")), new DataMap(asMap("key1" -> 1))),
      (
        new DataMap(asMap("key1" -> "1", "key2" -> "2")),
        new DataMap(asMap("key1" -> 1, "key2" -> 2)))) ++
      MAP_TEST_INPUTS
    testStringToPrimitiveCoercionValidation(schemaText, "bar", inputs, MAP_BAD_OBJECTS)
  }

  @Test
  def testUnionNormalCoercionValidation(): Unit = {
    val schemaText =
      """{"type": "record", "name": "foo", "fields": [{"name": "bar", "type": ["null", "int",
        |"string", {"type": "enum", "name": "Fruits", "symbols": ["APPLE", "ORANGE"]}]}]}""".stripMargin
    val inputs = List(
      (Data.NULL, Data.NULL),
      (new DataMap(asMap("int" -> 1)), new DataMap(asMap("int" -> 1))),
      (new DataMap(asMap("string" -> "x")), new DataMap(asMap("string" -> "x"))),
      (new DataMap(asMap("Fruits" -> "APPLE")), new DataMap(asMap("Fruits" -> "APPLE"))),
      (new DataMap(asMap("Fruits" -> "ORANGE")), new DataMap(asMap("Fruits" -> "ORANGE"))),
      (new DataMap(asMap("int" -> 1L)), new DataMap(asMap("int" -> 1))),
      (new DataMap(asMap("int" -> 1.0f)), new DataMap(asMap("int" -> 1))),
      (new DataMap(asMap("int" -> 1.0)), new DataMap(asMap("int" -> 1))))
    val badObjects = List(
      TRUE,
      I1,
      L1,
      F1,
      D1,
      new java.lang.String,
      new DataList,
      new DataMap(asMap("int" -> TRUE)),
      new DataMap(asMap("int" -> new java.lang.String("1"))),
      new DataMap(asMap("int" -> new DataMap)),
      new DataMap(asMap("int" -> new DataList)),
      new DataMap(asMap("string" -> TRUE)),
      new DataMap(asMap("string" -> I1)),
      new DataMap(asMap("string" -> L1)),
      new DataMap(asMap("string" -> F1)),
      new DataMap(asMap("string" -> D1)),
      new DataMap(asMap("string" -> new DataMap)),
      new DataMap(asMap("string" -> new DataList)),
      new DataMap(asMap("Fruits" -> I1)),
      new DataMap(asMap("Fruits" -> new DataMap)),
      new DataMap(asMap("Fruits" -> new DataList)),
      new DataMap(asMap("int" -> I1, "string" -> "x")),
      new DataMap(asMap("x" -> I1, "y" -> L1)))
    testNormalCoercionValidation(schemaText, "bar", inputs, badObjects)
  }

  @Test
  def testTyperefNormalCoercionValidation(): Unit = {
    val inputs =
      List(
        (I1, I1),
        (new java.lang.Integer(-1), new java.lang.Integer(-1)),
        (L1, I1),
        (F1, I1),
        (D1, I1))
    val badObjects = List(
      TRUE,
      new java.lang.String("abc"),
      ByteString.copyAvroString("bytes", false),
      new DataMap,
      new DataList)
    testNormalCoercionValidation(TYPEREF_SCHEMA, "bar1", inputs, badObjects)
    testNormalCoercionValidation(TYPEREF_SCHEMA, "bar2", inputs, badObjects)
    testNormalCoercionValidation(TYPEREF_SCHEMA, "bar3", inputs, badObjects)
    testNormalCoercionValidation(TYPEREF_SCHEMA, "bar4", inputs, badObjects)
  }

  @Test
  def testTyperefStringToPrimitiveCoercionValidation(): Unit = {
    val inputs = List(
      (new java.lang.String("1"), I1),
      (I1, I1),
      (IM1, IM1),
      (L1, I1),
      (F1, I1),
      (D1, I1))
    val badObjects = List(
      TRUE,
      new String("abc"),
      ByteString.copyAvroString("bytes", false),
      new DataMap,
      new DataList)
    testStringToPrimitiveCoercionValidation(TYPEREF_SCHEMA, "bar1", inputs, badObjects)
    testStringToPrimitiveCoercionValidation(TYPEREF_SCHEMA, "bar2", inputs, badObjects)
    testStringToPrimitiveCoercionValidation(TYPEREF_SCHEMA, "bar3", inputs, badObjects)
    testStringToPrimitiveCoercionValidation(TYPEREF_SCHEMA, "bar4", inputs, badObjects)
  }

  @Test
  def testRecordValidation(): Unit = {
    val schemaText =
      """{ "type" : "record", "name" : "foo", "fields" :
        |[{ "name" : "bar", "type" : { "name" : "barType", "type" : "record", "fields" :
        |  [{ "name" : "requiredInt", "type" : "int" },
        |   { "name" : "requiredString", "type" : "string" },
        |   { "name" : "defaultString", "type" : "string", "default" : "apple" },
        |   { "name" : "optionalBoolean", "type" : "boolean", "optional" : true },
        |   { "name" : "optionalDouble", "type" : "double", "optional" : true },
        |   { "name" : "optionalWithDefaultString", "type" : "string", "optional" : true,
        |     "default" : "orange" }] } } ] }""".stripMargin
    val input = List(
        ValidationOptions(
          requiredMode = RequiredMode.FIXUP_ABSENT_WITH_DEFAULT,
          coercionMode = CoercionMode.NORMAL) ->
        List(
          new DataMap(asMap("requiredInt" -> 12, "requiredString" -> "")),
          new DataMap(asMap("requiredInt" -> 34, "requiredString" -> "cow")),
          new DataMap(
            asMap("requiredInt" -> 56, "requiredString" -> "cat", "optionalBoolean" -> false)),
          new DataMap(
            asMap(
              "requiredInt" ->
                78,
              "requiredString" ->
                "dog",
              "optionalBoolean" ->
                true,
              "optionalDouble" ->
                999.5)),
          new DataMap(
            asMap(
              "requiredInt" ->
                78,
              "requiredString" ->
                "dog",
              "optionalBoolean" ->
                true,
              "optionalDouble" ->
                999.5,
              "optionalWithDefaultString" ->
                "tag")),
          new DataMap(
            asMap(
              "requiredInt" ->
                78,
              "requiredString" ->
                "dog",
              "extra1" ->
                TRUE)),
          new DataMap(
            asMap(
              "requiredInt" ->
                78,
              "requiredString" ->
                "dog",
              "optionalBoolean" ->
                true,
              "optionalDouble" ->
                999.5,
              "extra1" ->
                TRUE))))
    // All bad examples used CoercionMode.OFF which is unimplemented. So they have been skipped.
    testValidationWithDifferentValidationOptions(schemaText, "bar", input)
  }

  @Test
  def testValidationWithNormalCoercion(): Unit = {
    val key = "bar"
    val schema = dataSchemaFromString(SCHEMA_FOR_NORMAL_COERCION)
    val input =
      List(
        ValidationOptions(requiredMode = RequiredMode.CAN_BE_ABSENT_IF_HAS_DEFAULT),
        ValidationOptions(requiredMode = RequiredMode.FIXUP_ABSENT_WITH_DEFAULT)) ->
        // int
        List(
          new DataMap(asMap("int" -> 1L)) -> new DataMap(asMap("int" -> 1)),
          new DataMap(asMap("int" -> 1.0f)) -> new DataMap(asMap("int" -> 1)),
          new DataMap(asMap("int" -> 1.0)) -> new DataMap(asMap("int" -> 1)),
          // long
          new DataMap(asMap("long" -> 1)) -> new DataMap(asMap("long" -> 1L)),
          new DataMap(asMap("long" -> 1.0f)) -> new DataMap(asMap("long" -> 1L)),
          new DataMap(asMap("long" -> 1.0)) -> new DataMap(asMap("long" -> 1L)),
          // float
          new DataMap(asMap("float" -> 1)) -> new DataMap(asMap("float" -> 1.0f)),
          new DataMap(asMap("float" -> 1L)) -> new DataMap(asMap("float" -> 1.0f)),
          new DataMap(asMap("float" -> 1.0)) -> new DataMap(asMap("float" -> 1.0f)),
          // double
          new DataMap(asMap("double" -> 1)) -> new DataMap(asMap("double" -> 1.0)),
          new DataMap(asMap("double" -> 1L)) -> new DataMap(asMap("double" -> 1.0)),
          new DataMap(asMap("double" -> 1.0f)) -> new DataMap(asMap("double" -> 1.0)),
          // array of int's
          new DataMap(
            asMap("array" ->
              new DataList(asList(1, 2, 3, 1.0, 2.0, 3.0, 1.0f, 2.0f, 3.0f, 1.0, 2.0, 3.0)))) ->
            new DataMap(
              asMap("array" ->
                new DataList(asList(1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3)))),
          // map of int's
          new DataMap(
            asMap(
              "map"->
              new DataMap(asMap("int1"-> 1, "long"-> 1L, "float"-> 1.0f, "double"-> 1.0)))) ->
          new DataMap(
            asMap(
              "map"->
              new DataMap(
                asMap(
                  "int1"->
                  1,
                  "long"->
                  1,
                  "float"->
                  1,
                  "double"->
                  1)))),
          // record with int fields
          new DataMap(asMap("record"-> new DataMap(asMap("int"-> 1L)))) ->
          new DataMap(asMap("record"-> new DataMap(asMap("int"-> 1)))),

          new DataMap(asMap("record"-> new DataMap(asMap("int"-> 1.0f)))) ->
          new DataMap(asMap("record"-> new DataMap(asMap("int"-> 1)))),

          new DataMap(asMap("record"-> new DataMap(asMap("int"-> 1.0)))) ->
          new DataMap(asMap("record"-> new DataMap(asMap("int"-> 1)))),
          // union with int

          new DataMap(asMap("union"-> new DataMap(asMap("int"-> 1L)))) ->
          new DataMap(asMap("union"-> new DataMap(asMap("int"-> 1)))),

          new DataMap(asMap("union"-> new DataMap(asMap("int"-> 1.0f)))) ->
          new DataMap(asMap("union"-> new DataMap(asMap("int"-> 1)))),

          new DataMap(asMap("union"-> new DataMap(asMap("int"-> 1.0)))) ->
          new DataMap(
            asMap(
              "union"->
              new DataMap(asMap("int"-> 1)))),
          // union with record containing int

          new DataMap(
            asMap(
              "union"->
              new DataMap(asMap("recordType"-> new DataMap(asMap("int"-> 1L)))))) ->
          new DataMap(
            asMap(
              "union"->
              new DataMap(asMap("recordType"-> new DataMap(asMap("int"-> 1)))))),

          new DataMap(
            asMap(
              "union"->
              new DataMap(asMap("recordType"-> new DataMap(asMap("int"-> 1.0f)))))) ->
          new DataMap(
            asMap(
              "union"->
              new DataMap(asMap("recordType"-> new DataMap(asMap("int"-> 1)))))),

          new DataMap(
            asMap(
              "union"->
              new DataMap(asMap("recordType"-> new DataMap(asMap("int"-> 1.0)))))) ->
          new DataMap(
            asMap(
              "union"->
              new DataMap(asMap("recordType"-> new DataMap(asMap("int"-> 1))))))
        )
    testValidationWithNormalCoercionHelper(schema, key, input)
  }

  @Test
  def testValidationWithFixupAbsentWithDefault(): Unit = {
    val schemaText =
      """{ "type": "record", "name": "foo", "fields":
        | [ { "name": "bar", "type": { "name": "barType", "type": "record", "fields":
        |   [ {"name": "boolean", "type": "boolean", "default": true },
        |     {"name": "int", "type": "int", "default": 1 },
        |     {"name": "long", "type": "long", "default": 2 },
        |     {"name": "float", "type": "float", "default": 3.0 },
        |     {"name": "double", "type": "double", "default": 4.0 },
        |     {"name": "string", "type": "string", "default": "cow" },
        |     {"name": "bytes", "type": "bytes", "default": "dog" },
        |     {"name": "array", "type": { "type": "array", "items": "int" },
        |      "default": [ -1, -2, -3 ] },
        |     {"name": "enum", "type": { "type": "enum", "name": "enumType",
        |        "symbols": [ "apple", "orange", "banana" ] },
        |      "default": "apple" },
        |     {"name": "fixed", "type": { "type": "fixed", "name": "fixedType", "size": 4 },
        |      "default": "1234" },
        |     {"name": "map", "type": { "type": "map", "values": "int" },
        |      "default": { "1": 1, "2": 2 } },
        |     {"name": "record", "type": { "type": "record", "name": "recordType",
        |        "fields": [ { "name": "int", "type": "int" } ] },
        |      "default": { "int": 1 } },
        |     {"name" : "union", "type" : [ "int", "recordType", "enumType", "fixedType"],
        |      "default" : { "enumType" : "orange" } },
        |     {"name" : "unionWithNull", "type" : [ "null", "enumType", "fixedType" ],
        |      "default" : null },
        |     {"name" : "optionalInt", "type" : "int", "optional" : true },
        |     {"name" : "optionalDefaultInt", "type" : "int", "optional" : true,
        |      "default": 42 } ] } } ] }""".stripMargin
    val key = "bar"
    val schema = dataSchemaFromString(schemaText)
    assert(schema != null)
    val input =
        List(
          ValidationOptions(requiredMode =
            RequiredMode.FIXUP_ABSENT_WITH_DEFAULT)) ->
          List(new DataMap -> new DataMap(
            asMap(
              "boolean" ->
              true,
              "int" ->
              1,
              "long" ->
              2L,
              "float" ->
              3.0f,
              "double" ->
              4.0,
              "string" ->
              "cow",
              "bytes" ->
              ByteString.copyAvroString("dog", false),
              "array" ->
              new DataList(asList(-1, -2, -3)),
              "enum" ->
              "apple",
              "fixed" ->
              ByteString.copyAvroString("1234", false),
              "map" ->
              new DataMap(asMap("1"-> 1, "2"-> 2)),
              "record" ->
              new DataMap(asMap("int"-> 1)),
              "union" ->
              new DataMap(asMap("enumType" -> "orange")),
              "unionWithNull" ->
              Data.NULL)))
    testValidationWithNormalCoercionHelper(schema, key, input)
  }

  @Test
  def testNonRootStartDataElement(): Unit = {
    val schemaText =
      """{ "name": "Foo", "type": "record", "fields":
        |[{ "name": "intField", "type": "int", "optional": true },
        | { "name": "stringField", "type": "string", "optional": true },
        | { "name": "arrayField", "type": { "type": "array", "items": "Foo" }, "optional": true },
        | { "name": "mapField", "type": { "type": "map", "values": "Foo" }, "optional": true },
        | { "name": "unionField", "type": [ "int", "string", "Foo" ], "optional": true },
        | { "name": "fooField", "type": "Foo", "optional": true } ]}""".stripMargin
    val stringField = "/stringField"
    val error = "ERROR"
    val input: List[(String, String, List[String], List[String])] = List(
      (
        """{ "intField" : "bad", "fooField" : { "intField" : 32 } }""",
        "/fooField",
        List.empty,
        List("ERROR")),
      (
        """{ "intField" : 32, "fooField" : { "intField" : "bad" } }""",
        "/fooField",
        List[String](error, "/fooField/intField"),
        List.empty),
      (
        """{"stringField": 32, "arrayField": [{"intField": "bad0"}, {"intField": "bad1"}]}""",
        "/arrayField/0",
        List[String](error, "/arrayField/0/intField"),
        List[String](stringField, "/arrayField/1/intField")),
      (
        """{"stringField" : 32,
          | "mapField" : { "m0" : { "intField" : "bad0" },
          |                "m1" : { "intField" : "bad1" } }}"""
          .stripMargin,
        "/mapField/m1",
        List[String](error, "/mapField/m1/intField"),
        List[String](stringField, "/mapField/m0/intField")),
      (
        """
          |{"stringField": 32,
          | "arrayField": [{"unionField": {"Foo": {"intField": "bad0"}}},
          |                { "unionField": {"int": "bad1"}}]}""".stripMargin,
        "/arrayField/0/unionField",
        List[String](error, "/arrayField/0/unionField/Foo/intField"),
        List[String](stringField, "/arrayField/1/unionField/int")),
      (
        """
          |{"stringField" : 32,
          | "fooField" : {"stringField" : 45,
          |               "fooField": {"intField": "bad1" }}}}""".stripMargin,
        "/fooField/fooField",
        List[String](error, "/fooField/fooField/intField"),
        List[String](stringField, "/fooField/stringField")))
    val schema = dataSchemaFromString(schemaText)
    for (row <- input) {
      val (dataString, startPath, expectedStrings, notExpectedStrings) = row
      val map = dataMapFromString(dataString)
      val startElement = DataElementUtil.element(map, schema, startPath)
      assert(startElement ne null)
      val result = validate(startElement, ValidationOptions())
      val message = result.getMessages.toString
      for (expected <- expectedStrings) {
        assert(message.contains(expected), message + " does not contain " + expected)
      }
      for (notExpected <- notExpectedStrings) {
        assert(!message.contains(notExpected), message + " contains " + notExpected)
      }
    }
  }
}

object ValidateDataAgainstSchemaTest {

  val FALSE = new java.lang.Boolean(false)
  val TRUE = new java.lang.Boolean(true)

  val I1 = new java.lang.Integer(1)
  val IM1 = new java.lang.Integer(-1)
  val F1 = new java.lang.Float(1)
  val L1 = new java.lang.Long(1)
  val D1 = new java.lang.Double(1)

  val LONG_TEST_INPUTS = List(
    (new java.lang.Long(1), new java.lang.Long(1)),
    (new java.lang.Long(-1), new java.lang.Long(-1)),
    (new java.lang.Integer(1), new java.lang.Long(1)),
    (new java.lang.Float(1), new java.lang.Long(1)),
    (new java.lang.Double(1), new java.lang.Long(1)))

  val BAD_OBJECTS_FOR_NUMERIC = List(
    new java.lang.Boolean(true),
    new java.lang.String("abc"),
    ByteString.copyAvroString("bytes", false),
    new DataMap,
    new DataList)

  val FLOAT_TEST_INPUTS = List(
    (new java.lang.Float(1), new java.lang.Float(1)),
    (new java.lang.Float(-1), new java.lang.Float(-1)),
    (new java.lang.Integer(1), new java.lang.Float(1)),
    (new java.lang.Long(1), new java.lang.Float(1)),
    (new java.lang.Double(1), new java.lang.Float(1)))

  val DOUBLE_TEST_INPUTS = List(
    (new java.lang.Double(1), new java.lang.Double(1)),
    (new java.lang.Double(-1), new java.lang.Double(-1)),
    (new java.lang.Integer(1), new java.lang.Double(1)),
    (new java.lang.Long(1), new java.lang.Double(1)),
    (new java.lang.Float(1), new java.lang.Double(1)))

  val MAP_TEST_INPUTS = List(
    (new DataMap, new DataMap),
    (new DataMap(asMap("key1" -> 1)), new DataMap(asMap("key1" -> 1))),
    (new DataMap(asMap("key1" -> 1, "key2" -> 2)), new DataMap(asMap("key1" -> 1, "key2" -> 2))),
    (new DataMap(asMap("key1" -> 1L)), new DataMap(asMap("key1" -> 1))),
    (new DataMap(asMap("key1" -> 1.0)), new DataMap(asMap("key1" -> 1))),
    (new DataMap(asMap("key1" -> 1.0f)), new DataMap(asMap("key1" -> 1))),
    (new DataMap(asMap("key1" -> 1, "key2" -> 2L)), new DataMap(asMap("key1" -> 1, "key2" -> 2))),
    (
      new DataMap(asMap("key1" -> 1L, "key2" -> 2.0)),
      new DataMap(asMap("key1" -> 1, "key2" -> 2))))

  val MAP_BAD_OBJECTS = List(
      TRUE,
      I1,
      L1,
      F1,
      D1,
      new java.lang.String,
      new DataList,
      new DataMap(asMap("key1" -> TRUE)),
      new DataMap(asMap("key1" -> new DataMap)),
      new DataMap(asMap("key1" -> new DataList)))

  val ARRAY_TEST_INPUTS = List(
    (new DataList, new DataList),
    (new DataList(asList(1)), new DataList(asList(1))),
    (new DataList(asList(2, 3)), new DataList(asList(2, 3))),
    (new DataList(asList(1L)), new DataList(asList(1))),
    (new DataList(asList(1.0f)), new DataList(asList(1))),
    (new DataList(asList(1.0)), new DataList(asList(1))))

  val ARRAY_BAD_OBJECTS = List(
    TRUE,
    I1,
    L1,
    F1,
    D1,
    new java.lang.String,
    new DataMap,
    new DataList(asList(TRUE)),
    new DataList(asList(new DataMap)),
    new DataList(asList(new DataList)),
    new DataList(asList(TRUE, I1)),
    new DataList(asList(I1, TRUE)))

  private val codec = new JacksonDataCodec()

  def inputStreamFromString(s: String): ByteArrayInputStream = {
    val bytes = s.getBytes(Data.UTF_8_CHARSET)
    val bais = new ByteArrayInputStream(bytes)
    bais
  }

  def schemaParserFromString(s: String): SchemaParser = {
    val parser = new SchemaParser
    parser.parse(inputStreamFromString(s))
    parser
  }

  def dataSchemaFromString(s: String): DataSchema = {
    val parser = schemaParserFromString(s)
    if (parser.hasError) {
      println("ERROR: " + parser.errorMessage)
      null
    } else {
      parser.topLevelDataSchemas.get(parser.topLevelDataSchemas.size - 1)
    }
  }

  def validate(map: DataMap, schema: DataSchema, options: ValidationOptions): ValidationResult =
    ValidateDataAgainstSchema.validate(map, schema, options)

  def validate(element: DataElement, options: ValidationOptions): ValidationResult =
    ValidateDataAgainstSchema.validate(element, options, null)

  def normalCoercionValidationOption: ValidationOptions = {
    val options = ValidationOptions()
    assert(options.requiredMode == RequiredMode.CAN_BE_ABSENT_IF_HAS_DEFAULT)
    assert(options.coercionMode == CoercionMode.NORMAL)
    options
  }

  def stringToPrimitiveCoercionValidationOption: ValidationOptions = {
    val options = ValidationOptions(coercionMode = CoercionMode.STRING_TO_PRIMITIVE)
    assert(options.requiredMode == RequiredMode.CAN_BE_ABSENT_IF_HAS_DEFAULT)
    options
  }

  private def assertAllowedClass(coercionMode: CoercionMode, clazz: Class[_]): Unit = {
    assert((clazz eq classOf[java.lang.Integer]) || (clazz eq classOf[java.lang.Long]) || (clazz eq
      classOf[java.lang.Float]) ||
      (clazz eq classOf[java.lang.Double]) ||
      ((coercionMode == CoercionMode.STRING_TO_PRIMITIVE) && (clazz eq classOf[java.lang.String])))
  }

  def asList[T](refs: T*): java.util.List[T] = {
    val list = new java.util.ArrayList[T]()
    for (ref <- refs) {
      list.add(ref)
    }
    list
  }

  def asMap[V](kvs: (String, V)*): java.util.Map[String, V] = {
    val jmap = new java.util.HashMap[String, V]()
    for (kv <- kvs) {
      jmap.put(kv._1, kv._2)
    }
    jmap
  }

  def testValidationWithDifferentValidationOptions(
    schemaText: String,
    key: String,
    input: Seq[(ValidationOptions, Seq[AnyRef])]): Unit = {
    val schema = dataSchemaFromString(schemaText)
    assert(schema != null)
    val map = new DataMap
    for (rows <- input) {
      val (mode, dataObjects) = rows
      for (dataObject <- dataObjects) {
        map.put(key, dataObject)
        val result = validate(map, schema, mode)
        assert(result.isValid)
        if (!result.hasFix) {
          assert(map eq result.getFixed)
        }
      }
    }
  }

  private def testValidationWithNormalCoercionHelper(
      schema: DataSchema,
      key: String,
      input: (Seq[ValidationOptions], Seq[(DataMap, DataMap)])): Unit = {
    val (optionsList, pairs) = input
    for (options <- optionsList) {
      // Data object is read-only.
      assert(options.coercionMode == CoercionMode.NORMAL)
      for (pair <- pairs) {
        val foo = new DataMap
        foo.put(key, pair._1)
        foo.makeReadOnly()
        assert(foo.isReadOnly)
        assert(pair._1.asInstanceOf[DataComplex].isReadOnly)
        assert(foo.get(key) eq pair._1)
        val result = ValidateDataAgainstSchema.validate(foo, schema, options)
        assert(!result.isValid)
        assert(result.hasFix)
        assert(result.hasFixupReadOnlyError)
        assert(foo.isReadOnly)
        assert(pair._1.asInstanceOf[DataComplex].isReadOnly)
        val fooFixed = result.getFixed.asInstanceOf[DataMap]
        val barFixed = fooFixed.get(key)
        assert(pair._1 == barFixed) // not changed

        assert(fooFixed eq foo)
        assert(fooFixed.isReadOnly)
        assert(barFixed.asInstanceOf[DataComplex].isReadOnly)
        assert(barFixed eq pair._1)
      }
      // Data object is read-write
      for (pair <- pairs) {
        val foo = new DataMap
        val pair0 = pair._1.asInstanceOf[DataMap].copy // get read-write clone
        assert(!pair0.isReadOnly)
        foo.put(key, pair0)
        val result = validate(foo, schema, options)
        assert(result.isValid)
        val fooFixed = result.getFixed.asInstanceOf[DataMap]
        val barFixed = fooFixed.get(key)
        assert(result.isValid)
        assert(result.hasFix)
        assert(!result.hasFixupReadOnlyError)
        assert(!foo.isReadOnly)
        assert(!pair0.isReadOnly)
        assert(pair._2 == barFixed)
        assert(result.getFixed eq foo) // modify in place
        assert(!barFixed.asInstanceOf[DataComplex].isReadOnly)
        assert(barFixed eq pair0)
      }
    }
  }

  def dataMapFromString(json: String): DataMap = codec.stringToMap(json)

  val STRING_SCHEMA: String =
    """{"type": "record", "name": "foo", "fields": [{"name": "bar", "type": "string"}]}"""

  val BOOLEAN_SCHEMA: String =
    """{"type": "record", "name": "foo", "fields": [{"name": "bar", "type": "boolean"}]}"""

  val INTEGER_SCHEMA: String =
    """{"type": "record", "name": "foo", "fields": [{"name": "bar", "type": "int"}]}"""

  val TYPEREF_SCHEMA: String =
    """{ "type" : "record", "name" : "foo", "fields" : [
      | { "name" : "bar1", "type" : { "type" : "typeref", "name" : "int2", "ref": "int" }, "optional" : true },
      | { "name" : "bar2", "type" : "int2", "optional" : true },
      | { "name" : "bar3", "type" : { "type" : "typeref", "name" : "int3", "ref": "int2" }, "optional" : true },
      | { "name" : "bar4", "type" : "int3", "optional" : true }] }""".stripMargin

  val SCHEMA_FOR_NORMAL_COERCION: String =
    """ {
      |  "type": "record",
      |  "name": "foo",
      |  "fields": [
      |    {
      |      "name": "bar",
      |      "type": {
      |        "name": "barType",
      |        "type": "record",
      |        "fields": [
      |          {
      |            "name": "boolean",
      |            "type": "boolean",
      |            "optional": true
      |          },
      |          {
      |            "name": "int",
      |            "type": "int",
      |            "optional": true
      |          },
      |          {
      |            "name": "long",
      |            "type": "long",
      |            "optional": true
      |          },
      |          {
      |            "name": "float",
      |            "type": "float",
      |            "optional": true
      |          },
      |          {
      |            "name": "double",
      |            "type": "double",
      |            "optional": true
      |          },
      |          {
      |            "name": "string",
      |            "type": "string",
      |            "optional": true
      |          },
      |          {
      |            "name": "bytes",
      |            "type": "bytes",
      |            "optional": true
      |          },
      |          {
      |            "name": "array",
      |            "type": {
      |              "type": "array",
      |              "items": "int"
      |            },
      |            "optional": true
      |          },
      |          {
      |            "name": "enum",
      |            "type": {
      |              "type": "enum",
      |              "name": "enumType",
      |              "symbols": [
      |                "apple",
      |                "orange",
      |                "banana"
      |              ]
      |            },
      |            "optional": true
      |          },
      |          {
      |            "name": "fixed",
      |            "type": {
      |              "type": "fixed",
      |              "name": "fixedType",
      |              "size": 4
      |            },
      |            "optional": true
      |          },
      |          {
      |            "name": "map",
      |            "type": {
      |              "type": "map",
      |              "values": "int"
      |            },
      |            "optional": true
      |          },
      |          {
      |            "name": "record",
      |            "type": {
      |              "type": "record",
      |              "name": "recordType",
      |              "fields": [
      |                {
      |                  "name": "int",
      |                  "type": "int"
      |                }
      |              ]
      |            },
      |            "optional": true
      |          },
      |          {
      |            "name": "union",
      |            "type": [
      |              "int",
      |              "recordType",
      |              "enumType",
      |              "fixedType"
      |            ],
      |            "optional": true
      |          },
      |          {
      |            "name": "unionWithNull",
      |            "type": [
      |              "null",
      |              "enumType",
      |              "fixedType"
      |            ],
      |            "optional": true
      |          }
      |        ]
      |      }
      |    }
      |  ]
      |} """.stripMargin
}
