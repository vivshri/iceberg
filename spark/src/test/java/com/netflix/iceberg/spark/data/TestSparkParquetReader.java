/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.iceberg.spark.data;

import com.netflix.iceberg.Files;
import com.netflix.iceberg.Schema;
import com.netflix.iceberg.io.CloseableIterable;
import com.netflix.iceberg.io.FileAppender;
import com.netflix.iceberg.parquet.Parquet;
import com.netflix.iceberg.types.TypeUtil;
import com.netflix.iceberg.types.Types;
import org.apache.avro.generic.GenericData;
import org.apache.spark.sql.catalyst.InternalRow;
import org.junit.Assert;
import org.junit.Assume;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static com.netflix.iceberg.spark.data.TestHelpers.assertEqualsUnsafe;

public class TestSparkParquetReader extends AvroDataTest {
  protected void writeAndValidate(Schema schema) throws IOException {
    Assume.assumeTrue("Parquet Avro cannot write non-string map keys", null == TypeUtil.find(schema,
        type -> type.isMapType() && type.asMapType().keyType() != Types.StringType.get()));

    List<GenericData.Record> expected = RandomData.generateList(schema, 100, 0L);

    File testFile = temp.newFile();
    Assert.assertTrue("Delete should succeed", testFile.delete());

    try (FileAppender<GenericData.Record> writer = Parquet.write(Files.localOutput(testFile))
        .schema(schema)
        .named("test")
        .build()) {
      writer.addAll(expected);
    }

    try (CloseableIterable<InternalRow> reader = Parquet.read(Files.localInput(testFile))
        .project(schema)
        .createReaderFunc(type -> SparkParquetReaders.buildReader(schema, type))
        .build()) {
      Iterator<InternalRow> rows = reader.iterator();
      for (int i = 0; i < expected.size(); i += 1) {
        Assert.assertTrue("Should have expected number of rows", rows.hasNext());
        assertEqualsUnsafe(schema.asStruct(), expected.get(i), rows.next());
      }
      Assert.assertFalse("Should not have extra rows", rows.hasNext());
    }
  }
}
