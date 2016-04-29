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

class TypedDefinitionHandler extends UnionVariantHandler {
  // In order to tolerate the presence of the 'id' field, we currently do not fail on unrecognized
  // fields.
  private static final boolean FAIL_ON_UNRECOGNIZED_FIELDS = false;

  private final UnionDataSchema unionSchema;

  public TypedDefinitionHandler(
      TyperefDataSchema typerefSchema,
      UnionDataSchema unionSchema,
      DataMap mapping) {

    super(typerefSchema, mapping);
    this.unionSchema = unionSchema;
  }


  public String name() {
    return typedDefinitionField;
  }

  public PegasusUnionFormat convertToPegasus(DataMap dataMap) throws IOException {
    String typeName = lookupTypeName(dataMap);
    Object definition = lookupDefinition(dataMap);
    DataSchema memberSchema = typeNameToMemberSchema(typeName);

    if (FAIL_ON_UNRECOGNIZED_FIELDS && dataMap.size() != 2) {
      throw new IOException(
          "'" + name() + "' in '" + typerefSchema.getFullName() + "' requires data with only '" + typeName + "' and " +
              "'" + definitionField + "' fields, but found: " + dataMap.keySet() + ".");
    }

    dataMap.clear(); // remove the two fields
    dataMap.put(memberSchema.getUnionMemberKey(), definition);

    return new PegasusUnionFormat(memberSchema, definition);
  }

  public TypedDefinitionUnionFormat convertFromPegasus(DataMap dataMap) throws IOException {
    PegasusUnionFormat union = PegasusUnionFormat.fromDataMap(dataMap, unionSchema);
    String typeName = memberSchemaToTypeName(union.memberSchema);
    Object definition = union.definition();

    dataMap.clear();
    dataMap.put(typeNameField, typeName);
    dataMap.put(definitionField, definition);

    return new TypedDefinitionUnionFormat(
        dataMap,
        new PegasusUnionFormat(union.memberSchema, definition));
  }
}
