package org.coursera.naptime.courier

import com.linkedin.data.DataMap
import com.linkedin.data.codec.JacksonDataCodec
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import com.linkedin.data.template.DataTemplateUtil
import com.linkedin.data.template.PrettyPrinterJacksonDataTemplateCodec
import com.linkedin.data.template.RecordTemplate
import com.linkedin.data.template.UnionTemplate
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.pegasus.TypedDefinitionCodec

private[courier] object CourierTestFixtures {
  private[this] val jsonCodec = new JacksonDataCodec()
  private[this] val prettyPrinter = new PrettyPrinterJacksonDataTemplateCodec

  val pegasusUnionJson =
    s"""{
       |  "typedDefinition": {
       |    "UnionMember1": {
       |      "x": 1
       |    }
       |  }
       |}
       |""".stripMargin

  val pegasusUnionJsonWithUnrecognizedField =
    s"""{
       |  "typedDefinition": {
       |    "UnionMember1": {
       |      "x": 1
       |    }
       |  },
       |  "unrecognizedField": 2
       |}
       |""".stripMargin

  val pegasusUnionSchema =
    DataTemplateUtil.parseSchema("""
      |{
      |  "name": "TypedDefinitionExample1",
      |  "type": "record",
      |  "fields": [
      |    {
      |      "name": "typedDefinition", "type": {
      |        "name": "typedDefinitionUnionTyperef",
      |        "type": "typeref",
      |        "ref": [
      |          {
      |            "name": "UnionMember1",
      |            "type": "record",
      |            "fields": [
      |              { "name": "x", "type": "int" }
      |            ]
      |          },
      |          {
      |            "name": "UnionMember2",
      |            "type": "record",
      |            "fields": [
      |              { "name": "y", "type": "string" }
      |            ]
      |          }
      |        ]
      |      }
      |    }
      |  ]
      |}
      |""".stripMargin).asInstanceOf[RecordDataSchema]

  val typedDefinitionJson =
    s"""{
       |  "typedDefinition": {
       |    "typeName": "memberOne",
       |    "definition": {
       |      "x": 1
       |    }
       |  }
       |}
       |""".stripMargin

  val typedDefinitionJsonWithUnrecognizedField =
    s"""{
       |  "typedDefinition": {
       |    "typeName": "memberOne",
       |    "definition": {
       |      "x": 1
       |    }
       |  },
       |  "unrecognizedField": 2
       |}
       |""".stripMargin

  val flatTypedDefinitionJson =
    s"""{
       |  "typedDefinition": {
       |    "typeName": "memberOne",
       |    "x": 1
       |  }
       |}
       |""".stripMargin

  val flatTypedDefinitionJsonWithUnrecognizedField =
    s"""{
       |  "typedDefinition": {
       |    "typeName": "memberOne",
       |    "x": 1
       |  },
       |  "unrecognizedField": 2
       |}
       |""".stripMargin

  val typedDefinitionSchema =
    DataTemplateUtil.parseSchema("""
      |{
      |  "name": "TypedDefinitionExample1",
      |  "type": "record",
      |  "fields": [
      |    {
      |      "name": "typedDefinition", "type": {
      |        "name": "typedDefinitionUnionTyperef",
      |        "type": "typeref",
      |        "ref": [
      |          {
      |            "name": "UnionMember1",
      |            "type": "record",
      |            "fields": [
      |              { "name": "x", "type": "int" }
      |            ]
      |          },
      |          {
      |            "name": "UnionMember2",
      |            "type": "record",
      |            "fields": [
      |              { "name": "y", "type": "string" }
      |            ]
      |          }
      |        ],
      |        "typedDefinition": {
      |          "UnionMember1": "memberOne",
      |          "UnionMember2": "memberTwo"
      |        }
      |      }
      |    }
      |  ]
      |}
      |""".stripMargin).asInstanceOf[RecordDataSchema]

  val typedDefinitionCodec =
    new TypedDefinitionCodec(typedDefinitionSchema, prettyPrinter)

  val typedDefinitionCodecPassthroughEnabled =
    new TypedDefinitionCodec(typedDefinitionSchema, prettyPrinter, true)

  class TypedDefinitionRecord(private val dataMap: DataMap)
      extends RecordTemplate(dataMap, TypedDefinitionRecord.SCHEMA) {
    dataMap.makeReadOnly()
  }

  object TypedDefinitionRecord {
    val SCHEMA = typedDefinitionSchema

    def build(dataMap: DataMap, dataConversion: DataConversion) = {
      new TypedDefinitionRecord(dataMap)
    }
  }

  val flatTypedDefinitionSchema =
    DataTemplateUtil.parseSchema("""
      |{
      |  "name": "TypedDefinitionExample1",
      |  "type": "record",
      |  "fields": [
      |    {
      |      "name": "typedDefinition", "type": {
      |        "name": "typedDefinitionUnionTyperef",
      |        "type": "typeref",
      |        "ref": [
      |          {
      |            "name": "UnionMember1",
      |            "type": "record",
      |            "fields": [
      |              { "name": "x", "type": "int" }
      |            ]
      |          },
      |          {
      |            "name": "UnionMember2",
      |            "type": "record",
      |            "fields": [
      |              { "name": "y", "type": "string" }
      |            ]
      |          }
      |        ],
      |        "flatTypedDefinition": {
      |          "UnionMember1": "memberOne",
      |          "UnionMember2": "memberTwo"
      |        }
      |      }
      |    }
      |  ]
      |}
      |""".stripMargin).asInstanceOf[RecordDataSchema]

  val flatTypedDefinitionCodec =
    new TypedDefinitionCodec(flatTypedDefinitionSchema, jsonCodec)

  class FlatTypedDefinitionRecord(private val dataMap: DataMap)
      extends RecordTemplate(dataMap, FlatTypedDefinitionRecord.SCHEMA) {
    dataMap.makeReadOnly()
  }

  object FlatTypedDefinitionRecord {
    val SCHEMA = flatTypedDefinitionSchema

    def build(dataMap: DataMap, dataConversion: DataConversion) = {
      new FlatTypedDefinitionRecord(dataMap)
    }
  }

  val complexSchema = DataTemplateUtil.parseSchema(
    """
      |{
      |  "name": "Complex",
      |  "namespace": "org.example",
      |  "type": "record",
      |  "fields": [
      |    { "name": "int", "type": "int", "optional": true },
      |    { "name": "long", "type": "long", "optional": true },
      |    { "name": "float", "type": "float", "optional": true },
      |    { "name": "double", "type": "double", "optional": true },
      |    { "name": "boolean", "type": "boolean", "optional": true },
      |    { "name": "string", "type": "string", "optional": true },
      |    {
      |      "name": "record",
      |      "type": {
      |        "name": "Record",
      |        "namespace": "org.example",
      |        "type": "record",
      |        "fields": [
      |          { "name": "int", "type": "int", "optional": true }
      |        ]
      |      },
      |      "optional": true
      |    },
      |    { "name": "union", "type": [ "string", "Complex" ], "optional": true },
      |    { "name": "map", "type": { "type": "map", "values": "Complex" }, "optional": true },
      |    { "name": "array", "type": { "type": "array", "items": "Complex" }, "optional": true },
      |    {
      |      "name": "typedDefinition", "type": {
      |        "name": "TypedDefinition",
      |        "namespace": "org.example",
      |        "type": "typeref",
      |        "ref": [ "org.example.Complex", "Record" ],
      |        "typedDefinition": {
      |          "org.example.Complex": "complex",
      |          "Record": "record"
      |        }
      |      },
      |      "optional": true
      |    },
      |    {
      |      "name": "flatTypedDefinition", "type": {
      |        "name": "FlatTypedDefinition",
      |        "namespace": "org.example",
      |        "type": "typeref",
      |        "ref": [ "Complex", "org.example.Record" ],
      |        "flatTypedDefinition": {
      |          "Complex": "complex",
      |          "org.example.Record": "record"
      |        }
      |      },
      |      "optional": true
      |    }
      |  ]
      |}
      |""".stripMargin)

  val pegasusComplexJson =
    s"""{
       |  "int": 1,
       |  "long": 100,
       |  "float": 3.14,
       |  "double": 2.71,
       |  "boolean": true,
       |  "string": "hello",
       |  "record": { "int": 1 },
       |  "union": {
       |    "org.example.Complex": {
       |      "union": {
       |        "org.example.Complex": {
       |        }
       |      },
       |      "typedDefinition": {
       |        "org.example.Record": {
       |          "int": 1
       |        }
       |      },
       |      "flatTypedDefinition": {
       |        "org.example.Record": {
       |          "int": 1
       |        }
       |      }
       |    }
       |  },
       |  "map": {
       |    "key1": {
       |      "union": {
       |        "org.example.Complex": {
       |        }
       |      },
       |      "typedDefinition": {
       |        "org.example.Record": {
       |          "int": 1
       |        }
       |      },
       |      "flatTypedDefinition": {
       |        "org.example.Record": {
       |          "int": 1
       |        }
       |      }
       |    }
       |  },
       |  "array": [
       |    {
       |      "union": {
       |        "org.example.Complex": {
       |        }
       |      },
       |      "typedDefinition": {
       |        "org.example.Record": {
       |          "int": 1
       |        }
       |      },
       |      "flatTypedDefinition": {
       |        "org.example.Record": {
       |          "int": 1
       |        }
       |      }
       |    }
       |  ],
       |  "typedDefinition": {
       |    "org.example.Complex": {
       |      "union": {
       |        "org.example.Complex": {
       |        }
       |      },
       |      "typedDefinition": {
       |        "org.example.Record": {
       |          "int": 1
       |        }
       |      },
       |      "flatTypedDefinition": {
       |        "org.example.Record": {
       |          "int": 1
       |        }
       |      }
       |    }
       |  },
       |  "flatTypedDefinition": {
       |    "org.example.Complex": {
       |      "union": {
       |        "org.example.Complex": {
       |        }
       |      },
       |      "typedDefinition": {
       |        "org.example.Record": {
       |          "int": 1
       |        }
       |      },
       |      "flatTypedDefinition": {
       |        "org.example.Record": {
       |          "int": 1
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  val typedDefinitionComplexJson =
    s"""{
       |  "int": 1,
       |  "long": 100,
       |  "float": 3.14,
       |  "double": 2.71,
       |  "boolean": true,
       |  "string": "hello",
       |  "record": { "int": 1 },
       |  "union": {
       |    "org.example.Complex": {
       |      "union": {
       |        "org.example.Complex": {
       |        }
       |      },
       |      "typedDefinition": {
       |        "typeName": "record",
       |        "definition": {
       |          "int": 1
       |        }
       |      },
       |      "flatTypedDefinition": {
       |        "typeName": "record",
       |        "int": 1
       |      }
       |    }
       |  },
       |  "map": {
       |    "key1": {
       |      "union": {
       |        "org.example.Complex": {
       |        }
       |      },
       |      "typedDefinition": {
       |        "typeName": "record",
       |        "definition": {
       |          "int": 1
       |        }
       |      },
       |      "flatTypedDefinition": {
       |        "typeName": "record",
       |        "int": 1
       |      }
       |    }
       |  },
       |  "array": [
       |    {
       |      "union": {
       |        "org.example.Complex": {
       |        }
       |      },
       |      "typedDefinition": {
       |        "typeName": "record",
       |        "definition": {
       |          "int": 1
       |        }
       |      },
       |      "flatTypedDefinition": {
       |        "typeName": "record",
       |        "int": 1
       |      }
       |    }
       |  ],
       |  "typedDefinition": {
       |    "typeName": "complex",
       |    "definition": {
       |      "union": {
       |        "org.example.Complex": {
       |        }
       |      },
       |      "typedDefinition": {
       |        "typeName": "record",
       |        "definition": {
       |          "int": 1
       |        }
       |      },
       |      "flatTypedDefinition": {
       |        "typeName": "record",
       |        "int": 1
       |      }
       |    }
       |  },
       |  "flatTypedDefinition": {
       |    "typeName": "complex",
       |    "union": {
       |      "org.example.Complex": {
       |      }
       |    },
       |    "typedDefinition": {
       |      "typeName": "record",
       |      "definition": {
       |        "int": 1
       |      }
       |    },
       |    "flatTypedDefinition": {
       |      "typeName": "record",
       |      "int": 1
       |    }
       |  }
       |}
       |""".stripMargin

  val complexCodec = new TypedDefinitionCodec(complexSchema, jsonCodec)

  class MockRecord(private val dataMap: DataMap)
      extends RecordTemplate(dataMap, MockRecord.SCHEMA) {
    dataMap.makeReadOnly()
  }

  object MockRecord {
    val SCHEMA_JSON =
      """
       |{
       |  "name": "MockRecord",
       |  "type": "record",
       |  "fields": [
       |    { "name": "string", "type": "string" },
       |    { "name": "int", "type": "int" }
       |  ]
       |}
       |""".stripMargin

    val SCHEMA = DataTemplateUtil.parseSchema(SCHEMA_JSON).asInstanceOf[RecordDataSchema]

    def build(dataMap: DataMap, dataConversion: DataConversion) = {
      new MockRecord(dataMap)
    }
  }

  class MockTyperefUnion(private val dataMap: DataMap)
      extends UnionTemplate(dataMap, MockTyperefUnion.SCHEMA) {
    dataMap.makeReadOnly()
  }

  val mockTyperefUnionJson =
    s"""{
        |  "int": 1
        |}
        |""".stripMargin

  object MockTyperefUnion {
    val SCHEMA_JSON =
      """
        |[ "int", "string" ]
        |""".stripMargin

    val SCHEMA = DataTemplateUtil.parseSchema(SCHEMA_JSON).asInstanceOf[UnionDataSchema]

    val TYPEREF_SCHEMA_JSON =
      """
        |{
        |  "name": "MockTyperefUnion",
        |  "type": "typeref",
        |  "ref": [ "int", "string" ]
        |}
        |""".stripMargin

    val TYPEREF_SCHEMA =
      DataTemplateUtil.parseSchema(TYPEREF_SCHEMA_JSON).asInstanceOf[TyperefDataSchema]

    def build(dataMap: DataMap, dataConversion: DataConversion) = {
      new MockTyperefUnion(dataMap)
    }
  }
}
