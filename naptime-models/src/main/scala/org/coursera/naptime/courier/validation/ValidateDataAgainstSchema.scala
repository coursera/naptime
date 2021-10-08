package org.coursera.naptime.courier.validation

import java.math.BigDecimal
import java.util
import java.util.Collections

import com.linkedin.data.ByteString
import com.linkedin.data.Data
import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.element.DataElement
import com.linkedin.data.element.MutableDataElement
import com.linkedin.data.element.SimpleDataElement
import com.linkedin.data.it.IterationOrder
import com.linkedin.data.it.ObjectIterator
import com.linkedin.data.message.Message
import com.linkedin.data.message.MessageList
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.DataSchemaConstants
import com.linkedin.data.schema.FixedDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import com.linkedin.data.schema.validation.ValidationResult
import com.linkedin.data.schema.validator.Validator
import com.linkedin.data.schema.validator.ValidatorContext
import com.linkedin.data.schema.validation.{ValidationOptions => PegasusValidationOptions}

import scala.collection.JavaConverters._

object ValidateDataAgainstSchema {

  val _primitiveTypeToClassMap = Map(
    DataSchema.Type.INT -> classOf[Integer],
    DataSchema.Type.LONG -> classOf[java.lang.Long],
    DataSchema.Type.FLOAT -> classOf[java.lang.Float],
    DataSchema.Type.DOUBLE -> classOf[java.lang.Double],
    DataSchema.Type.STRING -> classOf[java.lang.String],
    DataSchema.Type.BOOLEAN -> classOf[java.lang.Boolean],
    DataSchema.Type.NULL -> classOf[com.linkedin.data.Null])

  def validate(
      obj: AnyRef,
      schema: DataSchema,
      options: ValidationOptions,
      validator: Validator): ValidationResult =
    validate(new SimpleDataElement(obj, schema), options, validator)

  def validate(obj: AnyRef, schema: DataSchema, options: ValidationOptions): ValidationResult =
    validate(obj, schema, options, null)

  def validate(
      element: DataElement,
      options: ValidationOptions,
      validator: Validator): ValidationResult = {
    val state = new State(options, validator)
    state.validate(element)
    state
  }

  private class State(options: ValidationOptions, validator: Validator) extends ValidationResult {

    private var _fixed: AnyRef = null
    private var _hasFixupReadOnlyError = false
    private var _hasFix: Boolean = false
    private var _valid: Boolean = true
    private val _messages: MessageList[Message] = new MessageList[Message]()
    private val context: Context = if (validator == null) {
      null
    } else {
      new Context()
    }

    override def hasFix: Boolean = _hasFix

    override def getMessages: util.Collection[Message] = Collections.unmodifiableList(_messages)

    override def hasFixupReadOnlyError: Boolean = _hasFixupReadOnlyError

    override def getFixed: AnyRef = _fixed

    override def isValid: Boolean = _valid

    def validate(element: DataElement): Unit = validateIterative(element)

    // maybe don't need this?
    def validateIterative(element: DataElement): Unit = {
      _fixed = element.getValue
      val it = new ObjectIterator(element, IterationOrder.POST_ORDER)
      var nextElement: DataElement = it.next()
      while (nextElement != null) {
        // do stuff here
        val nextElementSchema = nextElement.getSchema
        if (nextElementSchema != null) {
          validate(nextElement, nextElementSchema, nextElement.getValue)
        }
        nextElement = it.next()
      }
    }

    def validate(element: DataElement, schema: DataSchema, obj: AnyRef): AnyRef = {
      // can we do dynamic dispatch here?
      val fixed: AnyRef = schema.getType match {
        case DataSchema.Type.ARRAY =>
          validateArray(element, obj)
        case DataSchema.Type.BYTES =>
          validateBytes(element, obj)
        case DataSchema.Type.ENUM => validateEnum(element, obj)
        case DataSchema.Type.FIXED =>
          validateFixed(
            element,
            schema
              .asInstanceOf[FixedDataSchema],
            obj)
        case DataSchema.Type.MAP => validateMap(element, obj)
        case DataSchema.Type.RECORD =>
          validateRecord(
            element,
            schema
              .asInstanceOf[RecordDataSchema],
            obj)
        case DataSchema.Type.TYPEREF =>
          validateTyperef(
            element,
            schema
              .asInstanceOf[TyperefDataSchema],
            obj)
        case DataSchema.Type.UNION =>
          validateUnion(
            element,
            schema
              .asInstanceOf[UnionDataSchema],
            obj)
        case _ => validatePrimitive(element, schema, obj)
      }
      if (fixed != obj) {
        fixValue(element, fixed)
      }
      if (validator != null && element.getSchema == schema) {
        val validatorElement = if (fixed eq obj) {
          element
        } else {
          element match {
            case mutableElement: MutableDataElement =>
              mutableElement.setValue(fixed)
              element
            case _ =>
              new SimpleDataElement(fixed, element.getName, schema, element.getParent)
          }
        }
        context._el = validatorElement
        validator.validate(context)
      }
      fixed
    }

    protected def validatePrimitive(
        element: DataElement,
        schema: DataSchema,
        obj: AnyRef): AnyRef = {
      val primitiveClass = _primitiveTypeToClassMap(schema.getType)
      var fixed = obj
      if (obj.getClass != primitiveClass) {
        fixed = fixupPrimitive(schema, obj)
        if (fixed eq obj) {
          addMessage(
            element,
            "%1$s cannot be coerced to %2$s",
            String.valueOf(obj),
            primitiveClass.getSimpleName)
        }
      }
      fixed
    }

    protected def fixupPrimitive(schema: DataSchema, obj: AnyRef): AnyRef = {
      val schemaType = schema.getType
      try schemaType match {
        case DataSchema.Type.INT =>
          if (obj.isInstanceOf[Number]) {
            new java.lang.Integer(obj.asInstanceOf[Number].intValue)
          } else if ((obj.getClass eq classOf[String]) &&
                     (options.coercionMode == CoercionMode.STRING_TO_PRIMITIVE)) {
            new java.lang.Integer(new BigDecimal(obj.asInstanceOf[String]).intValue)
          } else {
            obj
          }
        case DataSchema.Type.LONG =>
          if (obj.isInstanceOf[Number]) {
            new java.lang.Long(obj.asInstanceOf[Number].longValue)
          } else if ((obj.getClass eq classOf[String]) &&
                     (options.coercionMode == CoercionMode.STRING_TO_PRIMITIVE)) {
            new java.lang.Long(new BigDecimal(obj.asInstanceOf[String]).longValue)
          } else {
            obj
          }
        case DataSchema.Type.FLOAT =>
          if (obj.isInstanceOf[Number]) {
            new java.lang.Float(obj.asInstanceOf[Number].floatValue)
          } else if ((obj.getClass eq classOf[String]) &&
                     (options.coercionMode == CoercionMode.STRING_TO_PRIMITIVE)) {
            new java.lang.Float(
              new BigDecimal(
                obj
                  .asInstanceOf[String]).floatValue)
          } else {
            obj
          }
        case DataSchema.Type.DOUBLE =>
          if (obj.isInstanceOf[Number]) {
            new java.lang.Double(obj.asInstanceOf[Number].doubleValue)
          } else if ((obj.getClass eq classOf[String]) &&
                     (options.coercionMode == CoercionMode.STRING_TO_PRIMITIVE)) {
            new java.lang.Double(
              new BigDecimal(
                obj
                  .asInstanceOf[String]).doubleValue)
          } else {
            obj
          }
        case DataSchema.Type.BOOLEAN =>
          if ((obj.getClass eq classOf[String]) &&
              (options.coercionMode == CoercionMode.STRING_TO_PRIMITIVE)) {
            val string = obj.asInstanceOf[String]
            if ("true".equalsIgnoreCase(string))
              java.lang.Boolean.TRUE
            else if ("false".equalsIgnoreCase(string)) java.lang.Boolean.FALSE
            else obj
          } else {
            obj
          }
        case DataSchema.Type.STRING | DataSchema.Type.NULL | _ =>
          obj
      } catch {
        case _: NumberFormatException =>
          obj
      }
    }

    protected def validateUnion(
        element: DataElement,
        schema: UnionDataSchema,
        obj: AnyRef): AnyRef = {
      obj match {
        case Data.NULL =>
          if (schema.getType(DataSchemaConstants.NULL_TYPE) == null) {
            addMessage(element, "null is not a member type of union %1$s", schema)
          }
        case map: DataMap =>
          if (map.size != 1) {
            addMessage(element, "DataMap should have exactly one entry for a union type")
          } else {
            val entry = map.entrySet.iterator.next
            val key = entry.getKey
            val memberSchema = schema.getType(key)
            if (memberSchema == null) {
              addMessage(element, "\"%1$s\" is not a member type of union %2$s", key, schema)
            }
          }
        case _ => addMessage(element, "union type is not backed by a DataMap or null")
      }
      obj
    }

    protected def validateTyperef(
        element: DataElement,
        schema: TyperefDataSchema,
        obj: AnyRef): AnyRef = validate(element, schema.getRef, obj)

    def validateArray(element: DataElement, obj: AnyRef): AnyRef = {
      if (!obj.isInstanceOf[DataList]) {
        addMessage(element, "array type is not backed by a DataList")
      }
      obj
    }

    def validateBytes(element: DataElement, obj: AnyRef): AnyRef = {
      var fixed: AnyRef = obj
      val clazz = obj.getClass
      if (clazz == classOf[String]) {
        val str = obj.asInstanceOf[String]
        var error = false
        val bytes = ByteString.copyAvroString(str, true)
        if (bytes != null) {
          _hasFix = true
          fixed = bytes
        } else {
          error = true
        }
        if (error) {
          addMessage(element, "\"%1$s\" is not a valid string representation of bytes", str)
        }
      } else if (clazz != classOf[ByteString]) {
        addMessage(element, "bytes type is not backed by a String or ByteString")
      }
      fixed
    }

    protected def validateMap(element: DataElement, obj: AnyRef): AnyRef = {
      if (!obj.isInstanceOf[DataMap]) {
        addMessage(element, "map type is not backed by a DataMap")
      }
      obj
    }

    def validateEnum(element: DataElement, obj: AnyRef): AnyRef = {
      if (!obj.isInstanceOf[String]) {
        addMessage(element, "enum type is not backed by a String")
      }
      obj
    }

    def validateFixed(element: DataElement, schema: FixedDataSchema, obj: AnyRef): AnyRef = {
      var fixed = obj
      val clazz = obj.getClass
      val size = schema.getSize
      if (clazz == classOf[String]) {
        val str = obj.asInstanceOf[String]
        var error = false
        if (str.length != size) {
          addMessage(
            element,
            "\"%1$s\" length (%2$d) is inconsistent with expected fixed size of %3$d",
            str,
            new java.lang.Integer(str.length),
            new java.lang.Integer(size))
        } else {
          val bytes = ByteString.copyAvroString(str, true)
          if (bytes != null) {
            _hasFix = true
            fixed = bytes
          } else {
            error = true
          }
        }
        if (error) {
          addMessage(element, "\"%1$s\" is not a valid string representation of bytes", str)
        }
      } else if (clazz == classOf[ByteString]) {
        val bytes = obj.asInstanceOf[ByteString]
        if (bytes.length != size) {
          addMessage(
            element,
            "\"%1$s\" length (%2$d) is inconsistent with expected fixed size of %3$d",
            bytes,
            new Integer(bytes.length),
            new Integer(size))
        }
      } else {
        addMessage(element, "fixed type is not backed by a String or ByteString")
      }
      fixed
    }

    protected def validateRecord(
        element: DataElement,
        schema: RecordDataSchema,
        obj: AnyRef): AnyRef = {
      obj match {
        case map: DataMap =>
          for (field <- schema.getFields.asScala) {
            if (!isFieldOptional(field, element) && !map.containsKey(field.getName)) {
              options.requiredMode match {
                case RequiredMode.CAN_BE_ABSENT_IF_HAS_DEFAULT =>
                  if (field.getDefault == null) {
                    addIsRequiredMessage(
                      element,
                      field,
                      "field is required but not found and has no default value")
                  }
                case RequiredMode.FIXUP_ABSENT_WITH_DEFAULT =>
                  val defaultValue = field.getDefault
                  if (defaultValue == null) {
                    addIsRequiredMessage(
                      element,
                      field,
                      "field is required but not found and has no default value")
                  } else if (map.isReadOnly) {
                    _hasFix = true
                    _hasFixupReadOnlyError = true
                    addIsRequiredMessage(
                      element,
                      field,
                      "field is required and has default value but not found and cannot be fixed because DataMap of record is read - only")

                  } else {
                    _hasFix = true
                    map.put(field.getName, defaultValue)
                  }

              }
            }
          }
        case _ => addMessage(element, "record type is not backed by a DataMap")
      }
      obj
    }

    private def isFieldOptional(field: RecordDataSchema.Field, element: DataElement): Boolean = {
      if (field.getOptional) {
        true
      } else {
        options.treatOptional
          .evaluate(new SimpleDataElement(null, field.getName, field.getType, element))
      }
    }

    protected def addIsRequiredMessage(
        element: DataElement,
        field: RecordDataSchema.Field,
        msg: String): Unit = {
      _messages.add(new Message(element.path(field.getName), msg))
      _valid = false
    }

    def addMessage(element: DataElement, format: String): Unit = {
      _messages.add(new Message(element.path(), format))
      _valid = false
    }

    def addMessage(element: DataElement, format: String, arg: AnyRef): Unit = {
      _messages.add(new Message(element.path(), format, arg))
      _valid = false
    }

    def addMessage(element: DataElement, format: String, arg1: AnyRef, arg2: AnyRef): Unit = {
      _messages.add(new Message(element.path(), format, arg1, arg2))
      _valid = false
    }

    def addMessage(
        element: DataElement,
        format: String,
        arg1: AnyRef,
        arg2: AnyRef,
        arg3: AnyRef): Unit = {
      _messages.add(new Message(element.path(), format, arg1, arg2, arg3))
      _valid = false
    }

    def fixValue(element: DataElement, fixed: AnyRef): Unit = {
      _hasFix = true
      val parentElement = element.getParent
      if (parentElement == null) {
        _fixed = fixed
      } else {
        val parent = parentElement.getValue
        if (parent.getClass == classOf[DataMap]) {
          val map = parent.asInstanceOf[DataMap]
          if (map.isReadOnly) {
            _hasFixupReadOnlyError = true
            addMessage(
              element,
              "cannot be fixed because DataMap backing %1$s type is read-only",
              parentElement.getSchema.getUnionMemberKey)
          } else {
            map.put(element.getName.asInstanceOf[String], fixed)
          }
        } else if (parent.getClass == classOf[DataList]) {
          val list = parent.asInstanceOf[DataList]
          if (list.isReadOnly) {
            _hasFixupReadOnlyError = true
            addMessage(
              element,
              "cannot be fixed because DataList backing an array type is read-only")
          } else {
            list.set(element.getName.asInstanceOf[Integer], fixed)
          }
        }
      }
    }

    private class Context extends ValidatorContext {
      var _el: DataElement = null

      override def addResult(message: Message): Unit = {
        _messages.add(message)
        if (message.isError) {
          _valid = false
        }
      }

      override def validationOptions(): PegasusValidationOptions =
        options.toPegasus

      override def dataElement(): DataElement = _el

      override def setHasFix(value: Boolean): Unit = _hasFix = value

      override def setHasFixupReadOnlyError(value: Boolean): Unit = _hasFixupReadOnlyError = value
    }
  }
}
