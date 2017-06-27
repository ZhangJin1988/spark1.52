/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// scalastyle:off println
package org.apache.spark.examples.sql.hive

import com.google.common.io.{ByteStreams, Files}

import java.io.File

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql._
import org.apache.spark.sql.hive.HiveContext

object HiveFromSpark {
  case class Record(key: Int, value: String)

  // Copy kv1.txt file from classpath to temporary directory
  //复制文件到临时目录中kv1.txt
  val kv1Stream = HiveFromSpark.getClass.getResourceAsStream("/kv1.txt")
  val kv1File = File.createTempFile("kv1", "txt")
  kv1File.deleteOnExit()
  ByteStreams.copy(kv1Stream, Files.newOutputStreamSupplier(kv1File))

  def main(args: Array[String]) {
    val sparkConf = new SparkConf().setMaster("local").setAppName("HiveFromSpark")
    val sc = new SparkContext(sparkConf)

    // A hive context adds support for finding tables in the MetaStore and writing queries
    // using HiveQL. Users who do not have an existing Hive deployment can still create a
    // HiveContext. When not configured by the hive-site.xml, the context automatically
    // creates metastore_db and warehouse in the current directory.
    val hiveContext = new HiveContext(sc)
    import hiveContext.implicits._
    import hiveContext.sql

    sql("USE default")
    sql("CREATE TABLE IF NOT EXISTS src (key INT, value STRING)")
    sql(s"LOAD DATA LOCAL INPATH '${kv1File.getAbsolutePath}' INTO TABLE src")

    // Queries are expressed in HiveQL
    //查询是表示在HiveQL
    println("Result of 'SELECT *': ")
    sql("SELECT * FROM src").collect().foreach(println)

    // Aggregation queries are also supported.
    //聚集查询也支持
    val count = sql("SELECT COUNT(*) FROM src").collect().head.getLong(0)
    println(s"COUNT(*): $count")

    // The results of SQL queries are themselves RDDs and support all normal RDD functions.  The
    //SQL查询的结果都是自己支持RDDs和所有正常的RDD功能
    // items in the RDD are of type Row, which allows you to access each column by ordinal.
    //在RDD项目类型的行,你可以按顺序访问每一列
    val rddFromSql = sql("SELECT key, value FROM src WHERE key < 10 ORDER BY key")

    println("Result of RDD.map:")
    val rddAsStrings = rddFromSql.map {
      case Row(key: Int, value: String) => s"Key: $key, Value: $value"
    }

    // You can also register RDDs as temporary tables within a HiveContext.
    //你也可以登记为临时表在hivecontext RDDS
    val rdd = sc.parallelize((1 to 100).map(i => Record(i, s"val_$i")))
    rdd.toDF().registerTempTable("records")

    // Queries can then join RDD data with data stored in Hive.
    //查询可以加入RDD数据与存储在Hive数据
    println("Result of SELECT *:")
    sql("SELECT * FROM records r JOIN src s ON r.key = s.key").collect().foreach(println)

    sc.stop()
  }
}
// scalastyle:on println
