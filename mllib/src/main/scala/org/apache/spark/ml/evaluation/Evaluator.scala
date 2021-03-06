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

package org.apache.spark.ml.evaluation

import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.ml.param.{ParamMap, Params}
import org.apache.spark.sql.DataFrame

/**
 * :: DeveloperApi ::
 * Abstract class for evaluators that compute metrics from predictions.
  * 评估的抽象类,用于根据预测计算度量
 */
@DeveloperApi
abstract class Evaluator extends Params {

  /**
   * Evaluates model output and returns a scalar metric (larger is better).
   * 计算模型输出并返回标量度量(较大为更好)
   * @param dataset a dataset that contains labels/observations and predictions.
   * @param paramMap parameter map that specifies the input columns and output metrics
   * @return metric
   */
  def evaluate(dataset: DataFrame, paramMap: ParamMap): Double = {
    this.copy(paramMap).evaluate(dataset)
  }

  /**
   * Evaluates the output.评估输出
   * @param dataset a dataset that contains labels/observations and predictions.
    *                包含标签/观察和预测的数据集
   * @return metric
   */
  def evaluate(dataset: DataFrame): Double

  /**
   * Indicates whether the metric returned by [[evaluate()]] should be maximized (true, default)
   * or minimized (false).
   * A given evaluator may support multiple metrics which may be maximized or minimized.
    *
    * 指示[[evaluate()]]返回的度量标准是最大化(true,default)还是最小化(false)
    * 给定的求值程序可以支持可以最大化或最小化的多个度量标准。
   */
  def isLargerBetter: Boolean = true

  override def copy(extra: ParamMap): Evaluator
}
