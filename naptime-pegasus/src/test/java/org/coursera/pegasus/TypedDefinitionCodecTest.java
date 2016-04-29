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
import com.linkedin.data.codec.JacksonDataCodec;
import com.linkedin.data.codec.TextDataCodec;
import com.linkedin.data.schema.DataSchema;
import com.linkedin.data.template.DataTemplateUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TypedDefinitionCodecTest {

  private static final TextDataCodec jsonCodec = new JacksonDataCodec();

  @Test
  public void testTypedDefinition() throws Throwable {
    DataMap deserialized = typedDefinitionCodec.stringToMap(typedDefinitionJson);
    assertSameJson(deserialized, pegasusUnionJson);
    deserialized.makeReadOnly();
    String serialized = typedDefinitionCodec.mapToString(deserialized);
    assertSameJson(serialized, typedDefinitionJson);
  }

  @Test
  public void testTypedDefinitionWithPrimitive() throws Throwable {
    DataMap deserialized =
        typedDefinitionWithPrimitiveCodec.stringToMap(typedDefinitionWithPrimitiveJson);
    assertSameJson(deserialized, pegasusUnionWithPrimitiveJson);
    deserialized.makeReadOnly();
    String serialized = typedDefinitionWithPrimitiveCodec.mapToString(deserialized);
    assertSameJson(serialized, typedDefinitionWithPrimitiveJson);
  }

  @Test
  public void testFlatTypedDefinition() throws Throwable {
    DataMap deserialized = flatTypedDefinitionCodec.stringToMap(flatTypedDefinitionJson);
    assertSameJson(deserialized, pegasusUnionJson);
    deserialized.makeReadOnly();
    String serialized = flatTypedDefinitionCodec.mapToString(dataMap(pegasusUnionJson));
    assertSameJson(serialized, flatTypedDefinitionJson);
  }

  @Test
  public void testComplexRecord() throws Throwable {
    DataMap deserialized = complexCodec.stringToMap(typedDefinitionComplexJson);
    assertSameJson(deserialized, pegasusComplexJson);
    deserialized.makeReadOnly();
    String serialized = complexCodec.mapToString(dataMap(pegasusComplexJson));
    assertSameJson(serialized, typedDefinitionComplexJson);
  }

  @Test
  public void testRemoveNulls() throws Throwable {
    DataMap deserialized = complexCodec.stringToMap(complexWithNullsJson);
    assertSameJson(deserialized, complexWithoutNullsJson);
    deserialized.makeReadOnly();
    String serialized = complexCodec.mapToString(dataMap(complexWithoutNullsJson));
    assertSameJson(serialized, complexWithoutNullsJson);
  }

  @Test
  public void testFilterUnrecognizedFieldsTypedDefinition() throws Throwable {
    DataMap deserialized =
        typedDefinitionCodec.stringToMap(typedDefinitionJsonWithUnrecognizedField);
    assertSameJson(deserialized, pegasusUnionJson);
    deserialized.makeReadOnly();
    String serialized =
      typedDefinitionCodec.mapToString(dataMap(pegasusUnionJsonWithUnrecognizedField));
    assertSameJson(serialized, typedDefinitionJson);
  }

  @Test
  public void testFilterOmittedFieldsTypedDefinition() throws Throwable {
    DataMap deserialized =
      typedDefinitionCodec.stringToMap(typedDefinitionJsonWithOmittedField);
    assertSameJson(deserialized, pegasusUnionJson);
    deserialized.makeReadOnly();
    String serialized =
      typedDefinitionCodec.mapToString(dataMap(pegasusUnionJsonWithOmittedField));
    assertSameJson(serialized, typedDefinitionJson);
  }

  @Test
  public void testFilterUnrecognizedFieldsFlatTypedDefinition() throws Throwable {
    DataMap deserialized =
      flatTypedDefinitionCodec.stringToMap(flatTypedDefinitionJsonWithUnrecognizedField);
    assertSameJson(deserialized, pegasusUnionJson);
    deserialized.makeReadOnly();
    String serialized =
      flatTypedDefinitionCodec.mapToString(dataMap(pegasusUnionJsonWithUnrecognizedField));
    assertSameJson(serialized, flatTypedDefinitionJson);
  }

  @Test
  public void testFilterOmittedFieldsFlatTypedDefinition() throws Throwable {
    DataMap deserialized =
      flatTypedDefinitionCodec.stringToMap(flatTypedDefinitionJsonWithOmittedField);
    assertSameJson(deserialized, pegasusUnionJson);
    deserialized.makeReadOnly();
    String serialized =
      flatTypedDefinitionCodec.mapToString(dataMap(pegasusUnionJsonWithOmittedField));
    assertSameJson(serialized, flatTypedDefinitionJson);
  }

  @Test
  public void testPassthroughEnabled() throws Throwable {
    DataMap deserialized =
      typedDefinitionCodecPassthroughEnabled.stringToMap(typedDefinitionJsonWithUnrecognizedField);
    deserialized.makeReadOnly();
    String roundTripped = typedDefinitionCodecPassthroughEnabled.mapToString(deserialized);
    assertSameJson(roundTripped, typedDefinitionJsonWithUnrecognizedField);
  }

  @Test
  public void testPassthroughExempt() throws Throwable {
    DataMap deserialized =
      passthroughExemptCodec.stringToMap(passthroughExemptJson);
    deserialized.makeReadOnly();
    String serialized =
      passthroughExemptCodec.mapToString(dataMap(passthroughExemptJson));
    assertSameJson(serialized, passthroughExemptJson);

  }

  private final TextDataCodec codec = new JacksonDataCodec();

  protected DataMap dataMap(String json) {
    try {
        return codec.stringToMap(json);
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
  }

  protected void assertSameJson(String json, String expectedJson) {
    Assert.assertEquals(dataMap(json), dataMap(expectedJson));
  }

  protected void assertSameJson(DataMap dataMap, String expectedJson) {
    Assert.assertEquals(dataMap, dataMap(expectedJson));
  }

  private String resource(String file) {
    return convertStreamToString(this.getClass().getResourceAsStream("/" + file));
  }

  private final DataSchema complexSchema = DataTemplateUtil.parseSchema(resource("complex.pdsc"));
  private final DataSchema pegasusUnionSchema =
      DataTemplateUtil.parseSchema(resource("pegasusUnion.pdsc"));
  private final DataSchema flatTypedDefinitionSchema =
      DataTemplateUtil.parseSchema(resource("flatTypedDefinition.pdsc"));
  private final DataSchema typedDefinitionSchema =
      DataTemplateUtil.parseSchema(resource("typedDefinition.pdsc"));
  private final DataSchema typedDefinitionWithPrimitiveSchema =
      DataTemplateUtil.parseSchema(resource("typedDefinitionWithPrimitive.pdsc"));
  private final DataSchema passthroughExemptSchema =
    DataTemplateUtil.parseSchema(resource("passthroughExempt.pdsc"));

  private final String pegasusUnionJson = resource("pegasusUnion.json");
  private final String pegasusUnionWithPrimitiveJson = resource("pegasusUnionWithPrimitive.json");
  private final String pegasusUnionJsonWithUnrecognizedField =
    resource("pegasusUnionWithUnrecognized.json");
  private final String pegasusUnionJsonWithOmittedField =
    resource("pegasusUnionWithOmitted.json");
  private final String typedDefinitionJson = resource("typedDefinition.json");
  private final String typedDefinitionWithPrimitiveJson = resource("typedDefinitionWithPrimitive.json");
  private final String typedDefinitionJsonWithUnrecognizedField =
    resource("typedDefinitionWithUnrecognized.json");
  private final String typedDefinitionJsonWithOmittedField =
    resource("typedDefinitionWithOmitted.json");
  private final String flatTypedDefinitionJson = resource("flatTypedDefinition.json");
  private final String flatTypedDefinitionJsonWithUnrecognizedField =
    resource("flatTypedDefinitionWithUnrecognized.json");
  private final String flatTypedDefinitionJsonWithOmittedField =
    resource("flatTypedDefinitionWithOmitted.json");
  private final String pegasusComplexJson = resource("pegasusComplex.json");
  private final String complexWithNullsJson = resource("complexWithNulls.json");
  private final String complexWithoutNullsJson = resource("complexWithoutNulls.json");
  private final String typedDefinitionComplexJson = resource("typedDefinitionComplex.json");
  private final String passthroughExemptJson = resource("passthroughExempt.json");

  private final TextDataCodec complexCodec =
      new TypedDefinitionCodec(complexSchema, jsonCodec);
  private final TextDataCodec flatTypedDefinitionCodec =
      new TypedDefinitionCodec(flatTypedDefinitionSchema, jsonCodec);
  private final TextDataCodec typedDefinitionCodec =
      new TypedDefinitionCodec(typedDefinitionSchema, jsonCodec);
  private final TextDataCodec typedDefinitionWithPrimitiveCodec =
      new TypedDefinitionCodec(typedDefinitionWithPrimitiveSchema, jsonCodec);

  private final TextDataCodec typedDefinitionCodecPassthroughEnabled =
      new TypedDefinitionCodec(typedDefinitionSchema, jsonCodec, true);

  private final TextDataCodec passthroughExemptCodec =
    new TypedDefinitionCodec(passthroughExemptSchema, jsonCodec);

  static String convertStreamToString(java.io.InputStream is) {
    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }
}
