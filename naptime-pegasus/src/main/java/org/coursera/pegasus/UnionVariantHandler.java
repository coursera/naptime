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

package org.coursera.pegasus;

import com.linkedin.data.DataMap;
import com.linkedin.data.schema.DataSchema;
import com.linkedin.data.schema.TyperefDataSchema;
import com.linkedin.data.schema.UnionDataSchema;

import java.io.IOException;

abstract class UnionVariantHandler {

  public static final String typedDefinitionField = "typedDefinition";
  public static final String flatTypedDefinitionField = "flatTypedDefinition";
  protected static final String typeNameField = "typeName";
  protected static final String definitionField = "definition";
  TyperefDataSchema typerefSchema;
  DataMap mapping;

  public UnionVariantHandler(TyperefDataSchema typerefSchema, DataMap mapping) {
    this.typerefSchema = typerefSchema;
    this.mapping = mapping;
  }

  abstract public String name();

  abstract public PegasusUnionFormat convertToPegasus(DataMap data) throws IOException;

  abstract public TypedDefinitionUnionFormat convertFromPegasus(DataMap data) throws IOException;

  public DataSchema typeNameToMemberSchema(String typeName) throws IOException {
    String memberKey = null;
    for (String key : mapping.keySet()) {
      Object value = mapping.get(key);
      if (value.equals(typeName)) {
        memberKey = key;
        break;
      }
    }
    if (memberKey == null) {
      throw new IOException(
          "No mapping in '" + name() + "' of '" + typerefSchema.getFullName() + "' found for typeName " +
              "'" + typeName + "'.");
    }

    DataSchema memberType = null;
    DataSchema deref = typerefSchema.getDereferencedDataSchema();
    if (deref instanceof UnionDataSchema) {
      UnionDataSchema unionSchema = (UnionDataSchema) deref;
      memberType = unionSchema.getType(memberKey);
      if (memberType == null) {
        if (!memberKey.contains(".")) {
          memberType = unionSchema.getType(typerefSchema.getNamespace() + "." + memberKey);
        }
      }
      if (memberType == null) {
        throw new IOException(
            "Union member '" + memberKey + "' not found for schema " + unionSchema);
      }
    }
    return memberType;
  }

  public String memberSchemaToTypeName(DataSchema memberSchema) throws IOException {
    // When serializing to typeDefinition, we fail fast if the mapping is missing, as a
    // writer should only attempt to write with memberKeys that have a correct mapping.
    String memberKey = memberSchema.getUnionMemberKey();
    Object type = mapping.get(memberKey);
    if (type == null) {
      String nsPrefix = typerefSchema.getNamespace() + ".";
      if (memberKey.startsWith(nsPrefix)) {
        String simpleName = memberKey.substring(nsPrefix.length());
        type = mapping.get(simpleName);
      }
    }
    if (type instanceof String) {
      return (String) type;

    } else {
      throw new IOException(
          "No mapping in '" + name() + "' of '" + typerefSchema.getFullName() + "' found for memberKey " +
              "'" + memberSchema.getUnionMemberKey() + "'. Mapping value must be a 'typeName' string.");
    }
  }

  public String lookupTypeName(DataMap dataMap) throws IOException {
    String result = dataMap.getString(typeNameField);
    if (result == null) {
      throw new IOException(
          "'" + name() + "' in '" + typerefSchema.getFullName() + "' requires data with a '" + typeNameField + "' " +
              "string, but found " + dataMap);
    }
    return result;
  }

  public Object lookupDefinition(DataMap dataMap) throws IOException {
    Object result = dataMap.get(definitionField);
    if (result == null) {
      throw new IOException(
          "'" + name() + "' in '" + typerefSchema.getFullName() + "' requires data with a '" + definitionField + "' " +
              "DataMap, but found " + dataMap);
    }
    return result;
  }

  static class TypedDefinitionUnionFormat {
    public final DataMap typedDefinitionData;
    public final PegasusUnionFormat pegasusUnionEquivalent;

    public TypedDefinitionUnionFormat(DataMap typedDefinitionData, PegasusUnionFormat pegasusUnionEquivalent) {
      this.typedDefinitionData = typedDefinitionData;
      this.pegasusUnionEquivalent = pegasusUnionEquivalent;
    }
  }

  static class PegasusUnionFormat {
    public final DataSchema memberSchema;
    public final Object definition;

    public PegasusUnionFormat(DataSchema memberSchema, Object definition) {
      this.memberSchema = memberSchema;
      this.definition = definition;
    }

    public Object definition() throws IOException {
      return definition;
    }

    public DataMap definitionAsDataMap() throws IOException {
      if (definition instanceof DataMap) {
        return (DataMap) definition;
      } else {
        throw new IOException("Type definition format requires a DataMap, but found: " + definition);
      }
    }

    public static PegasusUnionFormat fromDataMap(DataMap dataMap, UnionDataSchema unionSchema) throws IOException {
      if (dataMap.size() != 1) {
        throw new IOException(
            "Union DataMap must contain exactly one memberKey field, but " +
                "found fields: " + dataMap.keySet());
      }
      String memberKey = dataMap.keySet().iterator().next();
      Object definition = dataMap.get(memberKey);
      DataSchema memberSchema = unionSchema.getType(memberKey);
      if (memberSchema != null) {
        return new PegasusUnionFormat(memberSchema, definition);
      } else {
        throw new IOException("Union member '" + memberKey + "' not found for schema " + unionSchema);
      }
    }
  }

  static class UnionTyperefInfo {
    public final TyperefDataSchema typerefSchema;
    public final UnionDataSchema unionSchema;
    public final DataMap dataMap;

    public UnionTyperefInfo(TyperefDataSchema typerefSchema, UnionDataSchema unionSchema, DataMap dataMap) {
      this.typerefSchema = typerefSchema;
      this.unionSchema = unionSchema;
      this.dataMap = dataMap;
    }
  }
}
