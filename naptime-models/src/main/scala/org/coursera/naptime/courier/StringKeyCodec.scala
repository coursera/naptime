/*
 * Copyright 2016 Coursera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.coursera.naptime.courier

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.Charset

import com.linkedin.data.ByteString
import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.Null
import com.linkedin.data.codec.DataCodec
import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.BooleanDataSchema
import com.linkedin.data.schema.BytesDataSchema
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.DoubleDataSchema
import com.linkedin.data.schema.EnumDataSchema
import com.linkedin.data.schema.FloatDataSchema
import com.linkedin.data.schema.IntegerDataSchema
import com.linkedin.data.schema.LongDataSchema
import com.linkedin.data.schema.NullDataSchema
import com.linkedin.data.schema.PrimitiveDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.StringDataSchema

import scala.collection.JavaConverters._
import scala.collection.SortedSet
import scala.reflect.ClassTag
import scala.util.parsing.combinator.RegexParsers

/**
 * Intended for Legacy support only!
 *
 * This should only be used where compatibility with `StringKeyFormat` is required, for all other
 * uses, please see the more flexible `InlineStringCodec`.
 *
 * Provides a codec for Pegasus data that is compatible with `StringKeyFormat`.
 *
 * While this codec encodes data in the same format as `StringKeyFormat`, it is
 * designed to be used with Courier, not with Play JSON Formats.
 *
 * Limitations:
 * - The pegasus bytes type is not supported, consider base64 encoding to a string instead
 * - The pegasus union type is not supported, please migrate to `InlineStringCodec` if needed
 * - The pegasus map type is not supported, please migrate to `InlineStringCodec` if needed
 * - Records containing optional fields are not allowed
 *
 * This codec is "schema aided", meaning that the correct Pegasus schema is required to serialize
 * or deserialize data, even to the raw `DataMap` and `DataList` types. This is because
 * the order and names of record fields, defined in the schema, must be used by the codec to
 * correctly serialize/deserialize to `StringKeyFormat` tuples.
 *
 * The important type relations are:
 *
 * StringKeyFormat type | Pegasus raw type | Pegasus schema type | Scala type
 * ---------------------|------------------|---------------------|----------------------------------
 * Tuple                | DataMap          | record          | case class <TypeName>
 * Seq                  | DataList         | array           | <ItemName>Array extends IndexedSeq[T]
 *
 * The "Tuple" encoding
 * --------------------
 *
 * - Example: `Greetings~John~john@example.org`
 * - JSON Equivalent: `{ "email": "john@example.org", "message": "Greetings", "recipient": "John"}`
 * - Reserved chars: `!~`
 * - Escape char: `!`
 *
 * StringKeyFormat tuples are positionally ordered. The order of the tuple values must match the
 * order of fields in a pegasus record. E.g. the pegasus record:
 *
 * {{{
 *   {
 *     "name": "...",
 *     "type": "record",
 *     "fields": [
 *       { "name": "message", "type": "..." },
 *       { "name": "recipient", "type": "..." },
 *       { "name": "email", "type": "..." }
 *     ]
 *   }
 * }}}
 *
 * Would be required for the above tuple example.
 *
 * An empty string is parsed to a tuple of size 1 containing a single empty string.
 *
 * The "Seq" encoding
 * ------------------
 *
 * - Example: `one~two~three`
 * - JSON Equivalent: `[ "one", "two", "three" ]`
 * - Reserved chars: `!,`
 * - Escape char: `!`
 *
 * An empty string is parsed to an empty Seq.
 */
class StringKeyCodec(schema: DataSchema, prefix: Option[String] = None) extends DataCodec {

  private[this] val parser = new StringKeyCodec.Parser(prefix)
  private[this] val generator = new StringKeyCodec.Generator(prefix)

  override def writeMap(dataMap: DataMap, outputStream: OutputStream): Unit = {
    generator.generateMap(
      dataMap,
      requireSchemaType(schema, classOf[RecordDataSchema]),
      outputStream)
  }

  override def bytesToMap(bytes: Array[Byte]): DataMap = {
    readMap(new ByteArrayInputStream(bytes))
  }

  override def readMap(inputStream: InputStream): DataMap = {
    parser.parseMap(inputStream, requireSchemaType(schema, classOf[RecordDataSchema]))
  }

  override def mapToBytes(dataMap: DataMap): Array[Byte] = {
    val out = new ByteArrayOutputStream(StringKeyCodec.DEFAULT_BUFFER_SIZE)
    writeMap(dataMap, out)
    out.close()
    out.toByteArray
  }

  override def writeList(dataList: DataList, outputStream: OutputStream): Unit = {
    generator.generateList(
      dataList,
      requireSchemaType(schema, classOf[ArrayDataSchema]),
      outputStream)
  }

  override def bytesToList(bytes: Array[Byte]): DataList = {
    readList(new ByteArrayInputStream(bytes))
  }

  override def readList(inputStream: InputStream): DataList = {
    parser.parseList(inputStream, requireSchemaType(schema, classOf[ArrayDataSchema]))
  }

  override def listToBytes(dataList: DataList): Array[Byte] = {
    val out = new ByteArrayOutputStream(StringKeyCodec.DEFAULT_BUFFER_SIZE)
    writeList(dataList, out)
    out.close()
    out.toByteArray
  }

  private[this] def requireSchemaType[T: ClassTag](schema: DataSchema, clazz: Class[T]): T = {
    schema.getDereferencedDataSchema match {
      case matchingSchema: T => matchingSchema
      case unknown: DataSchema =>
        throw new IllegalArgumentException(
          s"Incompatible schema type: ${unknown.getClass}, ${clazz.getName} required.")
    }
  }
}

object StringKeyCodec {
  val charset = Charset.forName("UTF-8")

  private val DEFAULT_BUFFER_SIZE = 64

  private[this] val tupleParser =
    new StringListParser('!', '~', interpretEmptyInputAsEmptyList = false)

  private[this] val seqParser =
    new StringListParser('!', ',', interpretEmptyInputAsEmptyList = true)

  class Parser(prefix: Option[String] = None) {

    def parseMap[T](input: InputStream, schema: RecordDataSchema): DataMap = {
      toDataMap(tupleParser.parse(input), schema)
    }

    def parseList[T](input: InputStream, schema: ArrayDataSchema): DataList = {
      toDataList(seqParser.parse(input), schema)
    }

    def toDataMap(tuple: Seq[String], schema: RecordDataSchema): DataMap = {
      val fields = schema.getFields.asScala.toSeq
      val items = prefix match {
        case Some(expectedPrefix) =>
          val parsedPrefix = tuple.head
          if (tupleParser.unescape(parsedPrefix) != expectedPrefix) {
            throw new IOException(s"Incorrect prefix $parsedPrefix, expected $expectedPrefix")
          }
          tuple.tail
        case None => tuple
      }

      if (fields.size != items.size) {
        throw new IOException(s"Tuple length must be ${fields.size} but was ${items.size}")
      }

      val dataMap = new DataMap(fields.size)
      fields.zip(items).foreach {
        case (field, item) =>
          assert(!field.getOptional, "Records with optional fields are not supported.")
          dataMap.put(field.getName, parseData(item, field.getType))
      }
      dataMap
    }

    def toDataList(seq: Seq[String], schema: ArrayDataSchema): DataList = {
      val itemSchema = schema.getItems
      val items = seq

      val dataList = new DataList(items.size)
      items.foreach { item =>
        dataList.add(parseData(item, itemSchema))
      }
      dataList
    }

    private[this] def parseData(item: String, schema: DataSchema): AnyRef = {
      schema.getDereferencedDataSchema match {
        case _: BytesDataSchema =>
          throw new IOException(
            "'bytes' not supported, please consider base64 encoding a string instead.")
        case _: BooleanDataSchema =>
          new java.lang.Boolean(item)
        case _: IntegerDataSchema =>
          new java.lang.Integer(item)
        case _: LongDataSchema =>
          new java.lang.Long(item)
        case _: FloatDataSchema =>
          new java.lang.Float(item)
        case _: DoubleDataSchema =>
          new java.lang.Double(item)
        case _: StringDataSchema =>
          item
        case _: NullDataSchema =>
          Null.getInstance()
        // if primitives are all turned into strings like below instead of separated like above,
        // then the CoercionMode.STRING_TO_PRIMITIVE option must be used for validation
        /*
        case _: PrimitiveDataSchema =>
          item
         */
        case _: EnumDataSchema =>
          item
        case recordSchema: RecordDataSchema =>
          toDataMap(tupleParser.parse(item), recordSchema)
        case arraySchema: ArrayDataSchema =>
          toDataList(seqParser.parse(item), arraySchema)
        case unknown: DataSchema =>
          throw new IOException(s"Unsupported schema type: ${unknown.getClass}")
      }
    }
  }

  class Generator(prefix: Option[String] = None) {

    def generateList(dataList: DataList, schema: ArrayDataSchema): String = {
      // this format treats nested types as plain strings, so it's best to generate them to
      // strings and then escape them.
      val out = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE)
      generateList(dataList, schema, out)
      out.close()
      new String(out.toByteArray, charset)
    }

    def generateList(
        dataList: DataList,
        schema: ArrayDataSchema,
        outputStream: OutputStream): Unit = {
      val items = dataList.iterator().asScala
      val itemsSchema = schema.getItems

      items.zipWithIndex.foreach {
        case (item, idx) =>
          val valueString = generateData(item, itemsSchema)
          outputStream.write(seqParser.escape(valueString).getBytes(StringKeyCodec.charset))

          if (idx < dataList.size() - 1) {
            outputStream.write(seqParser.separatorBytes)
          }
      }
    }

    def generateMap(dataMap: DataMap, schema: RecordDataSchema): String = {
      // this format treats nested types as plain strings, so it's best to generate them to
      // strings and then escape them.
      val out = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE)
      generateMap(dataMap, schema, out)
      out.close()
      new String(out.toByteArray, charset)
    }

    def generateMap(
        dataMap: DataMap,
        schema: RecordDataSchema,
        outputStream: OutputStream): Unit = {
      val entries = dataMap.entrySet().asScala

      if (prefix.isDefined) {
        outputStream.write(tupleParser.escape(prefix.get).getBytes(StringKeyCodec.charset))
        if (entries.size > 0) {
          outputStream.write(tupleParser.separatorBytes)
        }
      }

      schema.getDereferencedDataSchema match {
        case record: RecordDataSchema =>
          val fields = record.getFields.asScala

          fields.zipWithIndex.foreach {
            case (field, idx) =>
              assert(!field.getOptional, "Records with optional fields are not supported.")
              val value = Option(dataMap.get(field.getName)).getOrElse {
                throw new IOException(s"Missing required field: ${field.getName}")
              }
              val valueString = generateData(value, field.getType)
              outputStream.write(
                tupleParser
                  .escape(valueString)
                  .getBytes(StringKeyCodec.charset))

              if (idx < fields.size - 1) {
                outputStream.write(tupleParser.separatorBytes)
              }
          }
        case unknown: DataSchema =>
          throw new IOException(s"Unsupported schema type: ${unknown.getClass}")
      }
    }

    private[this] def generateData(value: AnyRef, schema: DataSchema): String = {
      (value, schema.getDereferencedDataSchema) match {
        case (primitive: ByteString, _) =>
          throw new IOException(
            "'bytes' type not supported, consider base64 encoding to string instead.")
        case (primitive: AnyRef, primitiveSchema: PrimitiveDataSchema) =>
          value.toString
        case (enumSymbol: AnyRef, enumSchema: EnumDataSchema) =>
          value.toString
        case (map: DataMap, recordSchema: RecordDataSchema) =>
          generateMap(map, recordSchema)
        case (array: DataList, arraySchema: ArrayDataSchema) =>
          generateList(array, arraySchema)
        case (_, unknown: DataSchema) =>
          throw new IOException(s"Unsupported schema type: ${unknown.getClass}")
      }
    }
  }

  /**
   * Parses lists of strings.
   *
   * @param interpretEmptyInputAsEmptyList If true, an empty string is parsed as an empty
   * list. If false, an empty string is parsed as a list containing a single empty string.
   */
  private[this] class StringListParser(
      val escapeChar: Char,
      val separator: Char,
      val interpretEmptyInputAsEmptyList: Boolean = false)
      extends RegexParsers {

    val separatorBytes = separator.toString.getBytes(StringKeyCodec.charset)

    val reservedCharSet = SortedSet(escapeChar, separator)
    val reservedChars = reservedCharSet.mkString("")

    private[this] val escapeRegex = s"""([$reservedChars])""".r
    private[this] val unescapeRegex = s"""$escapeChar([$reservedChars])""".r

    def unescape(escaped: String): String = {
      unescapeRegex.replaceAllIn(escaped, "$1")
    }

    def escape(unescaped: String): String = {
      escapeRegex.replaceAllIn(unescaped, s"$escapeChar$$1")
    }

    def parse(input: InputStream): Seq[String] = {
      handleParseErrors(parseAll(listParser, new InputStreamReader(input)))
    }

    def parse(input: String): Seq[String] = {
      handleParseErrors(parseAll(listParser, input))
    }

    private[this] val itemRegex =
      s"""([^$reservedChars]|$escapeChar[$reservedChars])*""".r

    private[this] val itemParser: Parser[String] = {
      itemRegex ^^ { value =>
        unescape(value)
      }
    }

    private[this] val listParser: Parser[Seq[String]] = repsep(itemParser, separator) ^^ {
      case items: List[String] => {
        if (interpretEmptyInputAsEmptyList && items.size == 1 && items.head == "") {
          Seq.empty
        } else {
          items
        }
      }
    }

    private[this] def handleParseErrors[T](parseResult: ParseResult[T]): T = {
      parseResult match {
        case Success(result, _) => result
        case failure: NoSuccess =>
          throw new IOException(
            s"${failure.msg} line: ${failure.next.pos.line} column: ${failure.next.pos.column}")
      }
    }
  }
}
