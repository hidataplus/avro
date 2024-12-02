/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.avro.specific;

import org.apache.avro.Schema;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.util.ClassUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** {@link org.apache.avro.io.DatumReader DatumReader} for generated Java classes. */
public class SpecificDatumReader<T> extends GenericDatumReader<T> {

  public static final String[] SERIALIZABLE_PACKAGES;

  static {
    SERIALIZABLE_PACKAGES = System.getProperty("org.apache.avro.SERIALIZABLE_PACKAGES",
        "java.lang,java.math,java.io,java.net,org.apache.avro.reflect").split(",");
  }

  private final List<String> trustedPackages = new ArrayList<String>();

  public SpecificDatumReader() {
    this(null, null, SpecificData.get());
  }

  /** Construct for reading instances of a class. */
  public SpecificDatumReader(Class<T> c) {
    this(new SpecificData(c.getClassLoader()));
    setSchema(getSpecificData().getSchema(c));
  }

  /** Construct where the writer's and reader's schemas are the same. */
  public SpecificDatumReader(Schema schema) {
    this(schema, schema, SpecificData.get());
  }

  /** Construct given writer's and reader's schema. */
  public SpecificDatumReader(Schema writer, Schema reader) {
    this(writer, reader, SpecificData.get());
  }

  /** Construct given writer's schema, reader's schema, and a {@link
   * SpecificData}. */
  public SpecificDatumReader(Schema writer, Schema reader,
                             SpecificData data) {
    super(writer, reader, data);
    trustedPackages.addAll(Arrays.asList(SERIALIZABLE_PACKAGES));
  }

  /** Construct given a {@link SpecificData}. */
  public SpecificDatumReader(SpecificData data) {
    super(data);
  }

  /** Return the contained {@link SpecificData}. */
  public SpecificData getSpecificData() { return (SpecificData)getData(); }

  @Override
  public void setSchema(Schema actual) {
    // if expected is unset and actual is a specific record,
    // then default expected to schema of currently loaded class
    if (getExpected() == null && actual != null
        && actual.getType() == Schema.Type.RECORD) {
      SpecificData data = getSpecificData();
      Class c = data.getClass(actual);
      if (c != null && SpecificRecord.class.isAssignableFrom(c))
        setExpected(data.getSchema(c));
    }
    super.setSchema(actual);
  }

  @Override protected Class findStringClass(Schema schema) {
    Class stringClass = null;
    switch (schema.getType()) {
    case STRING:
      stringClass = getPropAsClass(schema, SpecificData.CLASS_PROP);
      break;
    case MAP: 
      stringClass = getPropAsClass(schema, SpecificData.KEY_CLASS_PROP);
      break;
    }
    if (stringClass != null)
      return stringClass;
    return super.findStringClass(schema);
  }

  private Class getPropAsClass(Schema schema, String prop) {
    String name = schema.getProp(prop);
    if (name == null) return null;
    try {
      // return ClassUtils.forName(getData().getClassLoader(), name);
      Class clazz = ClassUtils.forName(getData().getClassLoader(), name);
      checkSecurity(clazz);
      return clazz;
    } catch (ClassNotFoundException e) {
      throw new AvroRuntimeException(e);
    }
  }

  private boolean trustAllPackages() {
    return (trustedPackages.size() == 1 && "*".equals(trustedPackages.get(0)));
  }

  private void checkSecurity(Class clazz) throws ClassNotFoundException {
    if (trustAllPackages() || clazz.isPrimitive()) {
      return;
    }

    boolean found = false;
    Package thePackage = clazz.getPackage();
    if (thePackage != null) {
      for (String trustedPackage : getTrustedPackages()) {
        if (thePackage.getName().equals(trustedPackage) || thePackage.getName().startsWith(trustedPackage + ".")) {
          found = true;
          break;
        }
      }
      if (!found) {
        throw new SecurityException("Forbidden " + clazz
            + "! This class is not trusted to be included in Avro schema using java-class. Please set org.apache.avro.SERIALIZABLE_PACKAGES system property with the packages you trust.");
      }
    }
  }

  public final List<String> getTrustedPackages() {
    return trustedPackages;
  }

}

