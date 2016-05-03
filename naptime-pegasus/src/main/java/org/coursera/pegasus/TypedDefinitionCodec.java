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
import com.linkedin.data.codec.TextDataCodec;
import com.linkedin.data.schema.DataSchema;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

/**
 * Extends existing Pegasus codecs to support for the "typedDefinition" and "flatTypedDefinition"
 * serializations originally used in our Play! JSON classes. (See
 * JsonFormats.typedDefinitionFormat() and JsonFormats.flatTypedDefinitionFormat() in infra-services
 * for the Play! JSON implementation).
 * <br>
 * WARNING: This codec filters out unrecognized fields (any fields in the data that are not defined
 * in the schema) when reading and writing data by default.
 * It is only when this codec is used exclusively for reading and writing of data that passthrough
 * is guaranteed to propagate data in unrecognized fields correctly, in which case the
 * 'passthroughEnabled' may be safely set to true.
 * <br>
 * Usage:
 * In order to indicate a Pegasus union should be serialized using "typedDefinition" or
 * "flatTypedDefinition", a typeref must be defined for the union and annotated with a property
 * defining the mapping between the "memberKeys" of the Pegasus union and the "typeNames" of the
 * typed definition format.
 * <br>
 * For example, the Peagsus schema:
 * <pre>
 * {@code
 * {
 *   "name": "ExampleTypedDefinition",
 *   "type": "typeref",
 *   "ref": [ "org.example.TextEntry", "org.example.MultipleChoice" ],
 *   "typedDefinition": {
 *     "org.example.TextEntry": "textEntry",
 *     "org.example.MultipleChoice": "multipleChoice"
 *   }
 * }
 * }
 * </pre>
 * defines a "typedDefinition" union where the "memberKeys" of "org.example.TextEntry" and
 * "org.example.MultipleChoice" map to the "typeNames" of "textEntry" and "multipleChoice".
 * <br>
 * For a "flatTypedDefinition", instead do:
 * <br>
 * <pre>
 * {@code
 * {
 *   "name": "ExampleTypedDefinition",
 *   "type": "typeref",
 *   "ref": [ "org.example.TextEntry", "org.example.MultipleChoice" ],
 *   "flatTypedDefinition": {
 *     "org.example.TextEntry": "textEntry",
 *     "org.example.MultipleChoice": "multipleChoice"
 *   }
 * }
 * }
 * </pre>
 * <br>
 * "memberKeys" referencing types in the same namespace as the typeref may use simple names.
 * Fully qualified names are only required when referencing types in other namespaces.
 */
public class TypedDefinitionCodec implements TextDataCodec {

  private final TextDataCodec underlying;
  private final TypedDefinitionDataCoercer coercer;

  /**
   * @param schema     provides the schema of the data to serialize.
   * @param underlying provides the codec to use to serialize/deserialize.  Typically this
   *                   is [[JacksonDataCodec]], [[PrettyPrinterJacksonDataCodec]] or similar.
   */
  public TypedDefinitionCodec(DataSchema schema,
                              TextDataCodec underlying) {
    this(schema, underlying, false);
  }

  /**
   * @param schema             provides the schema of the data to serialize.
   * @param underlying         provides the codec to use to serialize/deserialize.  Typically this
   *                           is [[JacksonDataCodec]], [[PrettyPrinterJacksonDataCodec]] or similar.
   * @param passthroughEnabled configures if passthrough of recognized fields is enabled. Only
   *                           enable this when reading and writing data exclusively with this codec.
   */
  public TypedDefinitionCodec(DataSchema schema,
                              TextDataCodec underlying,
                              Boolean passthroughEnabled) {
    this.underlying = underlying;
    this.coercer = new TypedDefinitionDataCoercer(schema, passthroughEnabled);
  }

  public byte[] mapToBytes(DataMap map) throws IOException {
    return underlying.mapToBytes(coercer.convertUnionToTypedDefinition(map));
  }

  public void writeMap(DataMap map, OutputStream out) throws IOException {
    underlying.writeMap(coercer.convertUnionToTypedDefinition(map), out);
  }

  public DataMap bytesToMap(byte[] input) throws IOException {
    return coercer.convertTypedDefinitionToUnion(underlying.bytesToMap(input));
  }

  public DataMap readMap(InputStream in) throws IOException {
    return coercer.convertTypedDefinitionToUnion(underlying.readMap(in));
  }

  public byte[] listToBytes(DataList list) throws IOException {
    return underlying.listToBytes(coercer.convertUnionToTypedDefinition(list));
  }

  public void writeList(DataList list, OutputStream out) throws IOException {
    underlying.writeList(coercer.convertUnionToTypedDefinition(list), out);
  }

  public DataList bytesToList(byte[] input) throws IOException {
    return coercer.convertTypedDefinitionToUnion(underlying.bytesToList(input));
  }

  public DataList readList(InputStream in) throws IOException {
    return coercer.convertTypedDefinitionToUnion(underlying.readList(in));
  }

  public String getStringEncoding() {
    return underlying.getStringEncoding();
  }

  public String mapToString(DataMap map) throws IOException {
    return underlying.mapToString(coercer.convertUnionToTypedDefinition(map));
  }

  public void writeMap(DataMap map, Writer out) throws IOException {
    underlying.writeMap(coercer.convertUnionToTypedDefinition(map), out);
  }

  public DataList stringToList(String input) throws IOException {
    return coercer.convertTypedDefinitionToUnion(underlying.stringToList(input));
  }

  public DataMap readMap(Reader in) throws IOException {
    return coercer.convertTypedDefinitionToUnion(underlying.readMap(in));
  }

  public void writeList(DataList list, Writer out) throws IOException {
    underlying.writeList(coercer.convertUnionToTypedDefinition(list), out);
  }

  public DataMap stringToMap(String input) throws IOException {
    return coercer.convertTypedDefinitionToUnion(underlying.stringToMap(input));
  }

  public String listToString(DataList list) throws IOException {
    return listToString(coercer.convertUnionToTypedDefinition(list));
  }

  public DataList readList(Reader in) throws IOException {
    return coercer.convertTypedDefinitionToUnion(underlying.readList(in));
  }
}
