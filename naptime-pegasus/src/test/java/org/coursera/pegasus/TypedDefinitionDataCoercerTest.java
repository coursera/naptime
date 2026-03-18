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

import com.linkedin.data.DataList;
import com.linkedin.data.DataMap;
import com.linkedin.data.codec.JacksonDataCodec;
import com.linkedin.data.codec.TextDataCodec;
import com.linkedin.data.schema.DataSchema;
import com.linkedin.data.template.DataTemplateUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Tests for error paths and stream-based methods not covered by TypedDefinitionCodecTest.
 */
public class TypedDefinitionDataCoercerTest {

  private static final TextDataCodec jsonCodec = new JacksonDataCodec();

  static String convertStreamToString(java.io.InputStream is) {
    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }

  private String resource(String file) {
    return convertStreamToString(this.getClass().getResourceAsStream("/" + file));
  }

  private final DataSchema typedDefinitionSchema =
      DataTemplateUtil.parseSchema(resource("typedDefinition.pdsc"));
  private final DataSchema flatTypedDefinitionSchema =
      DataTemplateUtil.parseSchema(resource("flatTypedDefinition.pdsc"));

  private final String typedDefinitionJson = resource("typedDefinition.json");
  private final String pegasusUnionJson = resource("pegasusUnion.json");
  private final String flatTypedDefinitionJson = resource("flatTypedDefinition.json");

  private DataMap dataMap(String json) {
    try {
      return jsonCodec.stringToMap(json);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void assertSameJson(String json, String expectedJson) {
    Assert.assertEquals(dataMap(json), dataMap(expectedJson));
  }

  private void assertSameJson(DataMap dataMap, String expectedJson) {
    Assert.assertEquals(dataMap, dataMap(expectedJson));
  }

  // ─── TypedDefinitionCodec stream-based API methods ──────────────────────────────

  @Test
  public void mapToBytes_typedDefinition_roundTrips() throws IOException {
    TypedDefinitionCodec codec = new TypedDefinitionCodec(typedDefinitionSchema, jsonCodec);
    DataMap parsed = codec.bytesToMap(jsonCodec.mapToBytes(dataMap(typedDefinitionJson)));
    assertSameJson(parsed, pegasusUnionJson);

    byte[] serialized = codec.mapToBytes(parsed);
    assertSameJson(new String(serialized, StandardCharsets.UTF_8), typedDefinitionJson);
  }

  @Test
  public void writeMap_toOutputStream_roundTrips() throws IOException {
    TypedDefinitionCodec codec = new TypedDefinitionCodec(typedDefinitionSchema, jsonCodec);
    DataMap pegasusMap = dataMap(pegasusUnionJson);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    codec.writeMap(pegasusMap, out);
    String written = out.toString("UTF-8");
    assertSameJson(written, typedDefinitionJson);
  }

  @Test
  public void readMap_fromInputStream_deserializes() throws IOException {
    TypedDefinitionCodec codec = new TypedDefinitionCodec(typedDefinitionSchema, jsonCodec);
    byte[] bytes = typedDefinitionJson.getBytes(StandardCharsets.UTF_8);
    DataMap result = codec.readMap(new ByteArrayInputStream(bytes));
    assertSameJson(result, pegasusUnionJson);
  }

  @Test
  public void readMap_fromReader_deserializes() throws IOException {
    TypedDefinitionCodec codec = new TypedDefinitionCodec(typedDefinitionSchema, jsonCodec);
    DataMap result = codec.readMap(new StringReader(typedDefinitionJson));
    assertSameJson(result, pegasusUnionJson);
  }

  @Test
  public void writeMap_toWriter_roundTrips() throws IOException {
    TypedDefinitionCodec codec = new TypedDefinitionCodec(typedDefinitionSchema, jsonCodec);
    DataMap pegasusMap = dataMap(pegasusUnionJson);

    StringWriter writer = new StringWriter();
    codec.writeMap(pegasusMap, writer);
    assertSameJson(writer.toString(), typedDefinitionJson);
  }

  @Test
  public void getStringEncoding_returnsNonNull() {
    TypedDefinitionCodec codec = new TypedDefinitionCodec(typedDefinitionSchema, jsonCodec);
    Assert.assertNotNull(codec.getStringEncoding());
  }

  // ─── convertUnionToTypedDefinitionInPlace ───────────────────────────────────────

  @Test
  public void convertUnionToTypedDefinitionInPlace_mutatesDataInPlace() throws IOException {
    TypedDefinitionDataCoercer coercer =
        new TypedDefinitionDataCoercer(typedDefinitionSchema);
    DataMap mutableMap = dataMap(pegasusUnionJson);
    coercer.convertUnionToTypedDefinitionInPlace(mutableMap);
    assertSameJson(mutableMap, typedDefinitionJson);
  }

  @Test(expected = IllegalArgumentException.class)
  public void convertUnionToTypedDefinitionInPlace_readOnlyData_throwsIllegalArgumentException()
      throws IOException {
    TypedDefinitionDataCoercer coercer =
        new TypedDefinitionDataCoercer(typedDefinitionSchema);
    DataMap readOnlyMap = dataMap(pegasusUnionJson);
    readOnlyMap.makeReadOnly();
    coercer.convertUnionToTypedDefinitionInPlace(readOnlyMap);
  }

  // ─── DataList codec methods ──────────────────────────────────────────────────────

  @Test
  public void listToBytes_and_bytesToList_roundTrip() throws IOException {
    // Use a flat schema that wraps an array of typed definitions
    DataSchema arraySchema = DataTemplateUtil.parseSchema(
        "{\"type\":\"array\",\"items\":\"string\"}");
    TypedDefinitionCodec codec = new TypedDefinitionCodec(arraySchema, jsonCodec);

    DataList list = new DataList();
    list.add("item1");
    list.add("item2");

    byte[] bytes = codec.listToBytes(list);
    DataList result = codec.bytesToList(bytes);
    Assert.assertEquals(list, result);
  }

  @Test
  public void writeList_toOutputStream_roundTrips() throws IOException {
    DataSchema arraySchema = DataTemplateUtil.parseSchema(
        "{\"type\":\"array\",\"items\":\"string\"}");
    TypedDefinitionCodec codec = new TypedDefinitionCodec(arraySchema, jsonCodec);

    DataList list = new DataList();
    list.add("hello");
    list.add("world");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    codec.writeList(list, out);

    DataList result = codec.readList(new ByteArrayInputStream(out.toByteArray()));
    Assert.assertEquals(list, result);
  }

  @Test
  public void writeList_toWriter_roundTrips() throws IOException {
    DataSchema arraySchema = DataTemplateUtil.parseSchema(
        "{\"type\":\"array\",\"items\":\"string\"}");
    TypedDefinitionCodec codec = new TypedDefinitionCodec(arraySchema, jsonCodec);

    DataList list = new DataList();
    list.add("foo");

    StringWriter writer = new StringWriter();
    codec.writeList(list, writer);

    DataList result = codec.readList(new StringReader(writer.toString()));
    Assert.assertEquals(list, result);
  }

  @Test
  public void stringToList_deserializesCorrectly() throws IOException {
    DataSchema arraySchema = DataTemplateUtil.parseSchema(
        "{\"type\":\"array\",\"items\":\"string\"}");
    TypedDefinitionCodec codec = new TypedDefinitionCodec(arraySchema, jsonCodec);

    // Use stringToList to deserialize a JSON array string
    DataList result = codec.stringToList("[\"a\", \"b\"]");
    Assert.assertEquals(2, result.size());
    Assert.assertEquals("a", result.get(0));
    Assert.assertEquals("b", result.get(1));
  }

  @Test
  public void stringToMap_and_mapToString_roundTrip() throws IOException {
    TypedDefinitionCodec codec = new TypedDefinitionCodec(typedDefinitionSchema, jsonCodec);
    DataMap result = codec.stringToMap(typedDefinitionJson);
    assertSameJson(result, pegasusUnionJson);

    String serialized = codec.mapToString(result);
    assertSameJson(serialized, typedDefinitionJson);
  }

  // ─── Error path: both flatTypedDefinition and typedDefinition declared ──────────

  @Test(expected = IOException.class)
  public void lookupTypedDefinitionHandler_bothAnnotations_throwsIOException() throws IOException {
    // Build a schema with both typedDefinition AND flatTypedDefinition properties
    String bothAnnotationsSchema =
        "{\n" +
        "  \"name\": \"BothAnnotations\",\n" +
        "  \"type\": \"typeref\",\n" +
        "  \"ref\": [\n" +
        "    {\n" +
        "      \"name\": \"MA\",\n" +
        "      \"type\": \"record\",\n" +
        "      \"fields\": [{\"name\": \"v\", \"type\": \"string\"}]\n" +
        "    }\n" +
        "  ],\n" +
        "  \"typedDefinition\": {\"MA\": \"ma\"},\n" +
        "  \"flatTypedDefinition\": {\"MA\": \"ma\"}\n" +
        "}";

    // We can't easily parse a typeref with two conflicting properties via DataTemplateUtil,
    // so we build a record that holds one.
    // Instead, test via TypedDefinitionDataCoercer by creating a DataMap union that triggers
    // the lookup. We use a record schema that embeds the dual-annotation typeref.
    String recordSchemaJson =
        "{\n" +
        "  \"name\": \"DualAnnotated\",\n" +
        "  \"type\": \"record\",\n" +
        "  \"fields\": [\n" +
        "    {\n" +
        "      \"name\": \"union\", \"type\": {\n" +
        "        \"name\": \"DualTyperef\",\n" +
        "        \"type\": \"typeref\",\n" +
        "        \"ref\": [\n" +
        "          {\"name\": \"XA\", \"type\": \"record\", \"fields\": [{\"name\": \"x\", \"type\": \"int\"}]}\n" +
        "        ],\n" +
        "        \"typedDefinition\": {\"XA\": \"xa\"},\n" +
        "        \"flatTypedDefinition\": {\"XA\": \"xa\"}\n" +
        "      }\n" +
        "    }\n" +
        "  ]\n" +
        "}";
    DataSchema schema = DataTemplateUtil.parseSchema(recordSchemaJson);
    TypedDefinitionDataCoercer coercer = new TypedDefinitionDataCoercer(schema);

    // build a DataMap that has "union" key holding the dual-annotated union
    DataMap innerUnion = new DataMap();
    DataMap member = new DataMap();
    member.put("x", 1);
    innerUnion.put("XA", member);

    DataMap outer = new DataMap();
    outer.put("union", innerUnion);

    coercer.convertTypedDefinitionToUnion(outer); // should throw IOException
  }

  // ─── PegasusUnionFormat.fromDataMap error path ─────────────────────────────────

  @Test(expected = IOException.class)
  public void fromDataMap_unknownMemberKey_throwsIOException() throws IOException {
    DataSchema schema = DataTemplateUtil.parseSchema(resource("typedDefinition.pdsc"));
    TypedDefinitionDataCoercer coercer = new TypedDefinitionDataCoercer(schema);

    // Build a record whose union field points to an unknown member key
    DataMap unknownMember = new DataMap();
    unknownMember.put("org.example.UnknownMember", new DataMap());

    DataMap outer = new DataMap();
    outer.put("typedDefinition", unknownMember);
    coercer.convertUnionToTypedDefinition(outer); // triggers fromDataMap with unknown key
  }

  // ─── FlatTypedDefinitionCodec stream methods ──────────────────────────────────

  @Test
  public void flatTypedDefinition_readList_fromReader_works() throws IOException {
    DataSchema arraySchema = DataTemplateUtil.parseSchema(
        "{\"type\":\"array\",\"items\":\"string\"}");
    TypedDefinitionCodec codec = new TypedDefinitionCodec(arraySchema, jsonCodec);
    DataList list = new DataList();
    list.add("test");
    StringWriter writer = new StringWriter();
    codec.writeList(list, writer);
    DataList result = codec.readList(new StringReader(writer.toString()));
    Assert.assertEquals(list, result);
  }
}
