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


import com.linkedin.data.Data;
import com.linkedin.data.DataComplex;
import com.linkedin.data.DataList;
import com.linkedin.data.DataMap;
import com.linkedin.data.schema.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WARNING: This coercer mutates data in-place! Please do not use directly unless you are
 * sure you know what you are doing.  For most applications [[TypedDefinitionCodec]] should
 * be used.
 * <br>
 * See [[TypedDefinitionCodec]] for additional documentation.
 */
public class TypedDefinitionDataCoercer {

  private final DataSchema dataSchema;
  private final Boolean passthroughEnabled;

  public TypedDefinitionDataCoercer(DataSchema dataSchema) {
    this(dataSchema, false);
  }

  public TypedDefinitionDataCoercer(DataSchema dataSchema, Boolean passthroughEnabled) {
    this.dataSchema = dataSchema;
    this.passthroughEnabled = passthroughEnabled;
  }

  private interface Visitor {
    UnionVariantHandler.PegasusUnionFormat visit(UnionVariantHandler.UnionTyperefInfo info) throws IOException;
  }

  private class TypedDefinitionToPegasusVisitor implements Visitor {
    @Override
    public UnionVariantHandler.PegasusUnionFormat visit(UnionVariantHandler.UnionTyperefInfo info) throws IOException {
      UnionVariantHandler handler = lookupTypedDefinitionHandler(info);
      UnionVariantHandler.PegasusUnionFormat result = null;
      if (handler != null) {
        result = handler.convertToPegasus(info.dataMap);
      }
      if (result == null) {
        result = UnionVariantHandler.PegasusUnionFormat.fromDataMap(info.dataMap, info.unionSchema);
      }
      return result;
    }
  }

  private class TypedDefinitionFromPegasusVisitor implements Visitor {
    @Override
    public UnionVariantHandler.PegasusUnionFormat visit(UnionVariantHandler.UnionTyperefInfo info) throws IOException {
      UnionVariantHandler handler = lookupTypedDefinitionHandler(info);
      UnionVariantHandler.PegasusUnionFormat result = null;
      if (handler != null) {
        result = handler.convertFromPegasus(info.dataMap).pegasusUnionEquivalent;
      }
      if (result == null) {
        result = UnionVariantHandler.PegasusUnionFormat.fromDataMap(info.dataMap, info.unionSchema);
      }
      return result;
    }
  }

  /**
   * Convert from typedDefinition wire form to union form for in-memory usage by courier.
   */
  public <D extends DataComplex> D convertTypedDefinitionToUnion(D data) throws IOException {
    D copy = copyIfImmutable(data);
    visitUnionTyperefs(
        copy, dataSchema, new TypedDefinitionToPegasusVisitor(), new HashSet<DataComplex>());
    return copy;
  }

  /**
   * Convert from union form for in-memory usage by courier to typedDefinition wire form.
   */
  public <D extends DataComplex> D convertUnionToTypedDefinition(D data) throws IOException {
    D copy = copyIfImmutable(data);
    visitUnionTyperefs(
        copy, dataSchema, new TypedDefinitionFromPegasusVisitor(), new HashSet<DataComplex>());
    return copy;
  }

  /**
   * Convert from union form for in-memory usage by courier to typedDefinition wire form.
   */
  public <D extends DataComplex> void convertUnionToTypedDefinitionInPlace(D data)
          throws IOException, IllegalArgumentException{
    if (data.isReadOnly()) {
      throw new IllegalArgumentException("Data is read only!");
    }
    visitUnionTyperefs(
            data, dataSchema, new TypedDefinitionFromPegasusVisitor(), new HashSet<DataComplex>());
  }

  // Not usually needed for Java pegasus bindings.
  private <D extends DataComplex> D copyIfImmutable(D data) {
    // TODO(jbetz): Benchmark. If performance is poor, consider using copy-on-write maps here.
    try {
      return (D) data.copy();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  private UnionVariantHandler lookupTypedDefinitionHandler(UnionVariantHandler.UnionTyperefInfo info) throws IOException {
    TyperefDataSchema typerefSchema = info.typerefSchema;
    UnionDataSchema unionSchema = info.unionSchema;
    Map<String, Object> properties = typerefSchema.getProperties();

    Object flatDefinition = properties.get(UnionVariantHandler.flatTypedDefinitionField);
    Object definition = properties.get(UnionVariantHandler.typedDefinitionField);

    if (flatDefinition != null && definition != null) {
      throw new IOException("'flatTypedDefinition' or 'typedDefinition' may " +
          "be declared on " + typerefSchema.getFullName() + ", not both.");
    } else if (flatDefinition != null && (flatDefinition instanceof DataMap)) {
      return new FlatTypedDefinitionHandler(typerefSchema, unionSchema, (DataMap) flatDefinition);
    } else if (definition != null && (definition instanceof DataMap)) {
      return new TypedDefinitionHandler(typerefSchema, unionSchema, (DataMap) definition);
    } else {
      return null;
    }
  }


// I had hoped to use ObjectIterator from Pegasus data, as it supports schema aware
// data traversal, but I need access to typeref information, and ObjectIterator dereferences
// data schemas during traversal so they are not available to the visitor.

// Because ObjectIterator already exists for general purpose traversal and the traversal we
// must do here is very specific to our needs, I've decided not to generalize it any further
// than what is here.


  private void visitUnionTyperefs(
      Object data, DataSchema schema, Visitor visitor, Set<DataComplex> visited) throws IOException {
    if (data instanceof DataComplex) {
      if (visited.contains(data)) {
        return;
      }
    }

    DataSchema deref = schema.getDereferencedDataSchema();
    switch (deref.getType()) {
      case RECORD:
        RecordDataSchema recordSchema = (RecordDataSchema) deref;
        if (data instanceof DataMap) {
          DataMap dataMap = (DataMap) data;
          List<String> removals = new ArrayList<String>(0);
          for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == Data.NULL) {
              // Quick fix: Strip out nulls from data since the Pegasus validator
              // currently chokes on them.  This will be removed once we fix the
              // Pegasus validator to properly handle nulls as absent optional fields.
              removals.add(key);
            } else {
              RecordDataSchema.Field field = recordSchema.getField(key);
              if (field != null && !isScalaOmitted(field)) {
                visitUnionTyperefs(value, field.getType(), visitor, visited);
              } else {
                if (!passthroughEnabled && !passthroughExempt(recordSchema)) {
                  removals.add(key);
                }
              }
            }
          }
          for (String removal : removals) { // filter out unrecognized field
            dataMap.remove(removal);
          }
        }
        break;
      case UNION:
        UnionDataSchema unionSchema = (UnionDataSchema) deref;
        if (data instanceof DataMap) {
          DataMap unionDataMap = (DataMap) data;
          UnionVariantHandler.PegasusUnionFormat pegasusUnion = null;
          switch (schema.getType()) {
            case TYPEREF:
              // Found a typeref'd union.  Apply our visitor to it.
              pegasusUnion = visitor.visit(
                  new UnionVariantHandler.UnionTyperefInfo(
                    (TyperefDataSchema) schema, unionSchema, unionDataMap));
              break;
            case UNION:
              pegasusUnion = UnionVariantHandler.PegasusUnionFormat.fromDataMap(
                unionDataMap, unionSchema);
              break;
          }
          if (pegasusUnion != null) {
            visitUnionTyperefs(
              pegasusUnion.definition, pegasusUnion.memberSchema, visitor, visited);
          }
        }
        break;
      case ARRAY:
        ArrayDataSchema arraySchema = (ArrayDataSchema) deref;
        if (data instanceof DataList) {
          DataList dataList = (DataList) data;
          DataSchema itemsSchema = arraySchema.getItems();
          for (Object dataItem : dataList.values()) {
            visitUnionTyperefs(dataItem, itemsSchema, visitor, visited);
          }
        }
        break;
      case MAP:
        MapDataSchema mapSchema = (MapDataSchema) deref;
        if (data instanceof DataMap) {
          DataMap map = (DataMap) data;
          DataSchema valuesSchema = mapSchema.getValues();
          for (Object value : map.values()) {
            visitUnionTyperefs(value, valuesSchema, visitor, visited);
          }
        }
        break;
      default:
        break;
    }
    if (data instanceof DataComplex) {
      visited.add((DataComplex) data);
    }
  }

  private boolean isScalaOmitted(RecordDataSchema.Field field) {
    DataMap properties = scalaProperties(field);
    if (properties != null) {
      Boolean omit = (Boolean)properties.get("omit");
      return (omit != null && omit);
    }
    else {
      return false;
    }
  }

  private DataMap scalaProperties(RecordDataSchema.Field field) {
    Map<String, Object> properties = field.getProperties();
    if (properties != null) {
      return (DataMap) properties.get("scala");
    } else {
      return null;
    }
  }

  private boolean passthroughExempt(RecordDataSchema record) {
    Map<String, Object> properties = record.getProperties();
    if (properties != null) {
      Object passthroughExempt = properties.get("passthroughExempt");
      return Boolean.TRUE.equals(passthroughExempt);
    } else {
      return false;
    }
  }
}
