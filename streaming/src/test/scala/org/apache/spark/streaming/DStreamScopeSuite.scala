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

package org.apache.spark.streaming

import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

import org.apache.spark.{SparkContext, SparkFunSuite}
import org.apache.spark.rdd.RDDOperationScope
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.ui.UIUtils

/**
 * Tests whether scope information is passed from DStream operations to RDDs correctly.
 * 测试从dstream的RDDs正确通过操作范围信息
 */
class DStreamScopeSuite extends SparkFunSuite with BeforeAndAfter with BeforeAndAfterAll {
  private var ssc: StreamingContext = null
  private val batchDuration: Duration = Seconds(1)//批量处理间隔1秒

  override def beforeAll(): Unit = {
  //分隔的时间叫作批次间隔
    ssc = new StreamingContext(new SparkContext("local", "test"), batchDuration)
  }

  override def afterAll(): Unit = {
    ssc.stop(stopSparkContext = true)
  }

  before { assertPropertiesNotSet() }
  after { assertPropertiesNotSet() }

  test("dstream without scope") {//没有范围
    val dummyStream = new DummyDStream(ssc)
    dummyStream.initialize(Time(0))
    //DStream没有在范围内初始化
    // This DStream is not instantiated in any scope, so all RDDs
    // created by this stream should similarly not have a scope
    //所以这个流创建的所有RDDS应该同样没有范围
    assert(dummyStream.baseScope === None)
    //getOrCompute从缓存generatedRDDs = new HashMap[Time,RDD[T]]中获取RDD,
    //如果缓存中不存在,则生成RDD并持久化,设置检查点并放入缓存
    assert(dummyStream.getOrCompute(Time(1000)).get.scope === None)
    assert(dummyStream.getOrCompute(Time(2000)).get.scope === None)
    assert(dummyStream.getOrCompute(Time(3000)).get.scope === None)
  }

  test("input dstream without scope") {//输入dstream没有范围
    val inputStream = new DummyInputDStream(ssc)
    inputStream.initialize(Time(0))
   //没有输入流范围
    val baseScope = inputStream.baseScope.map(RDDOperationScope.fromJson)
     //getOrCompute从缓存generatedRDDs = new HashMap[Time,RDD[T]]中获取RDD,
    //如果缓存中不存在,则生成RDD并持久化,设置检查点并放入缓存
    val scope1 = inputStream.getOrCompute(Time(1000)).get.scope
    val scope2 = inputStream.getOrCompute(Time(2000)).get.scope
    val scope3 = inputStream.getOrCompute(Time(3000)).get.scope

    // This DStream is not instantiated in any scope, so all RDDs
    //dstream不在任何范围的实例化
    assertDefined(baseScope, scope1, scope2, scope3)
    assert(baseScope.get.name.startsWith("dummy stream"))
    assertScopeCorrect(baseScope.get, scope1.get, 1000)
    assertScopeCorrect(baseScope.get, scope2.get, 2000)
    assertScopeCorrect(baseScope.get, scope3.get, 3000)
  }

  test("scoping simple operations") {//简单作用域操作
    val inputStream = new DummyInputDStream(ssc)
    val mappedStream = inputStream.map { i => i + 1 }
    val filteredStream = mappedStream.filter { i => i % 2 == 0 }
    filteredStream.initialize(Time(0))

    val mappedScopeBase = mappedStream.baseScope.map(RDDOperationScope.fromJson)
    val mappedScope1 = mappedStream.getOrCompute(Time(1000)).get.scope
    val mappedScope2 = mappedStream.getOrCompute(Time(2000)).get.scope
    val mappedScope3 = mappedStream.getOrCompute(Time(3000)).get.scope
    val filteredScopeBase = filteredStream.baseScope.map(RDDOperationScope.fromJson)
    val filteredScope1 = filteredStream.getOrCompute(Time(1000)).get.scope
    val filteredScope2 = filteredStream.getOrCompute(Time(2000)).get.scope
    val filteredScope3 = filteredStream.getOrCompute(Time(3000)).get.scope

    // These streams are defined in their respective scopes "map" and "filter", so all
    //这些流被定义在它们各自的作用域中"map" and "filter",
    // RDDs created by these streams should inherit the IDs and names of their parent
    // DStream's base scopes
    assertDefined(mappedScopeBase, mappedScope1, mappedScope2, mappedScope3)
    assertDefined(filteredScopeBase, filteredScope1, filteredScope2, filteredScope3)
    assert(mappedScopeBase.get.name === "map")
    assert(filteredScopeBase.get.name === "filter")
    assertScopeCorrect(mappedScopeBase.get, mappedScope1.get, 1000)
    assertScopeCorrect(mappedScopeBase.get, mappedScope2.get, 2000)
    assertScopeCorrect(mappedScopeBase.get, mappedScope3.get, 3000)
    assertScopeCorrect(filteredScopeBase.get, filteredScope1.get, 1000)
    assertScopeCorrect(filteredScopeBase.get, filteredScope2.get, 2000)
    assertScopeCorrect(filteredScopeBase.get, filteredScope3.get, 3000)
  }

  test("scoping nested operations") {//作用域嵌套操作
    val inputStream = new DummyInputDStream(ssc)
    //countByWindow对所有元素进行count操作后,每个RDD都只包含一个元素的新的DStream
    val countStream = inputStream.countByWindow(Seconds(10), Seconds(1))
    countStream.initialize(Time(0))

    val countScopeBase = countStream.baseScope.map(RDDOperationScope.fromJson)
    val countScope1 = countStream.getOrCompute(Time(1000)).get.scope
    val countScope2 = countStream.getOrCompute(Time(2000)).get.scope
    val countScope3 = countStream.getOrCompute(Time(3000)).get.scope

    // Assert that all children RDDs inherit the DStream operation name correctly
    assertDefined(countScopeBase, countScope1, countScope2, countScope3)
    assert(countScopeBase.get.name === "countByWindow")
    assertScopeCorrect(countScopeBase.get, countScope1.get, 1000)
    assertScopeCorrect(countScopeBase.get, countScope2.get, 2000)
    assertScopeCorrect(countScopeBase.get, countScope3.get, 3000)

    // All streams except the input stream should share the same scopes as `countStream`
    //除了输入流的所有数据流必须共享相同的范围"countstream"
    def testStream(stream: DStream[_]): Unit = {
      if (stream != inputStream) {
        val myScopeBase = stream.baseScope.map(RDDOperationScope.fromJson)
	 //getOrCompute从缓存generatedRDDs = new HashMap[Time,RDD[T]]中获取RDD,
	 //如果缓存中不存在,则生成RDD并持久化,设置检查点并放入缓存
        val myScope1 = stream.getOrCompute(Time(1000)).get.scope
        val myScope2 = stream.getOrCompute(Time(2000)).get.scope
        val myScope3 = stream.getOrCompute(Time(3000)).get.scope
        assertDefined(myScopeBase, myScope1, myScope2, myScope3)
        assert(myScopeBase === countScopeBase)
        assert(myScope1 === countScope1)
        assert(myScope2 === countScope2)
        assert(myScope3 === countScope3)
        // Climb upwards to test the parent streams
        stream.dependencies.foreach(testStream)
      }
    }
    testStream(countStream)
  }

  /** 
   *  Assert that the RDD operation scope properties are not set in our SparkContext.
   *  断言RDD操作范围属性没有设置sparkcontext 
   *  */
  private def assertPropertiesNotSet(): Unit = {
    assert(ssc != null)
    assert(ssc.sc.getLocalProperty(SparkContext.RDD_SCOPE_KEY) == null)
    assert(ssc.sc.getLocalProperty(SparkContext.RDD_SCOPE_NO_OVERRIDE_KEY) == null)
  }

  /** 
   *  Assert that the given RDD scope inherits the name and ID of the base scope correctly.
   *  断言了RDD范围继承了基地范围的名称和ID正确 
   *  */
  private def assertScopeCorrect(
      baseScope: RDDOperationScope,
      rddScope: RDDOperationScope,
      batchTime: Long): Unit = {
    assertScopeCorrect(baseScope.id, baseScope.name, rddScope, batchTime)
  }

  /** 
   *  Assert that the given RDD scope inherits the base name and ID correctly. 
   *  断言了RDD范围继承的基名称和ID正确。
   *  */
  private def assertScopeCorrect(
      baseScopeId: String,
      baseScopeName: String,
      rddScope: RDDOperationScope,
      batchTime: Long): Unit = {
    val formattedBatchTime = UIUtils.formatBatchTime(
      batchTime, ssc.graph.batchDuration.milliseconds, showYYYYMMSS = false)
    assert(rddScope.id === s"${baseScopeId}_$batchTime")
    assert(rddScope.name.replaceAll("\\n", " ") === s"$baseScopeName @ $formattedBatchTime")
  }

  /** 
   *  Assert that all the specified options are defined.
   *  断言所有指定的选项都被定义 
   *  */
  private def assertDefined[T](options: Option[T]*): Unit = {
    options.zipWithIndex.foreach { case (o, i) => assert(o.isDefined, s"Option $i was empty!") }
  }

}
