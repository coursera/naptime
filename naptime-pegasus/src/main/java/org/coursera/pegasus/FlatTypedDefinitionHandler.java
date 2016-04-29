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

class FlatTypedDefinitionHandler extends UnionVariantHandler {
  private final UnionDataSchema unionSchema;

  public FlatTypedDefinitionHandler(
      TyperefDataSchema typerefSchema,
      UnionDataSchema unionSchema,
      DataMap mapping) {

    super(typerefSchema, mapping);
    this.unionSchema = unionSchema;
  }

  public String name() {
    return flatTypedDefinitionField;
  }

  public PegasusUnionFormat convertToPegasus(DataMap dataMap) throws IOException {
    String typeName = lookupTypeName(dataMap);
    DataSchema memberSchema = typeNameToMemberSchema(typeName);

    dataMap.remove(typeNameField);
    // TODO(jbetz): Benchmark this.  Is a shallow copy acceptable here?
    try {
      DataMap definition = dataMap.clone(); // Shallow copy
      dataMap.clear();

      dataMap.put(memberSchema.getUnionMemberKey(), definition);

      return new PegasusUnionFormat(memberSchema, definition);

    } catch (CloneNotSupportedException e) {
      throw new IOException((e));
    }
  }

  public TypedDefinitionUnionFormat convertFromPegasus(DataMap dataMap) throws IOException {
    PegasusUnionFormat union = PegasusUnionFormat.fromDataMap(dataMap, unionSchema);
    String typeName = memberSchemaToTypeName(union.memberSchema);
    DataMap definition = union.definitionAsDataMap();

    dataMap.clear();
    dataMap.put(typeNameField, typeName);
    dataMap.putAll(definition);

    return new TypedDefinitionUnionFormat(
        dataMap, new PegasusUnionFormat(union.memberSchema, definition));
  }
}
