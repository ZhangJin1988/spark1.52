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

package org.apache.spark.storage

import scala.collection.mutable

import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.scheduler._

/**
 * :: DeveloperApi ::
 * A SparkListener that maintains executor storage status.
  * 维护执行程序存储状态的SparkListener
 *
 * This class is thread-safe (unlike JobProgressListener)
  * 这个类是线程安全的（不同于JobProgressListener）
 */
@DeveloperApi
class StorageStatusListener extends SparkListener {
  // This maintains only blocks that are cached (i.e. storage level is not StorageLevel.NONE)
  //这仅维护缓存的块(即,存储级别不是StorageLevel.NONE)
  private[storage] val executorIdToStorageStatus = mutable.Map[String, StorageStatus]()

  def storageStatusList: Seq[StorageStatus] = synchronized {
    executorIdToStorageStatus.values.toSeq
  }

  /** Update storage status list to reflect updated block statuses
    * 更新存储状态列表以反映更新的块状态 */
  private def updateStorageStatus(execId: String, updatedBlocks: Seq[(BlockId, BlockStatus)]) {
    executorIdToStorageStatus.get(execId).foreach { storageStatus =>
      updatedBlocks.foreach { case (blockId, updatedStatus) =>
        if (updatedStatus.storageLevel == StorageLevel.NONE) {
          storageStatus.removeBlock(blockId)
        } else {
          storageStatus.updateBlock(blockId, updatedStatus)
        }
      }
    }
  }

  /** Update storage status list to reflect the removal of an RDD from the cache
    * 更新存储状态列表以反映从缓存中删除RDD */
  private def updateStorageStatus(unpersistedRDDId: Int) {
    storageStatusList.foreach { storageStatus =>
      storageStatus.rddBlocksById(unpersistedRDDId).foreach { case (blockId, _) =>
        storageStatus.removeBlock(blockId)
      }
    }
  }

  override def onTaskEnd(taskEnd: SparkListenerTaskEnd): Unit = synchronized {
    val info = taskEnd.taskInfo
    val metrics = taskEnd.taskMetrics
    if (info != null && metrics != null) {
      val updatedBlocks = metrics.updatedBlocks.getOrElse(Seq[(BlockId, BlockStatus)]())
      if (updatedBlocks.length > 0) {
        updateStorageStatus(info.executorId, updatedBlocks)
      }
    }
  }

  override def onUnpersistRDD(unpersistRDD: SparkListenerUnpersistRDD): Unit = synchronized {
    updateStorageStatus(unpersistRDD.rddId)
  }

  override def onBlockManagerAdded(blockManagerAdded: SparkListenerBlockManagerAdded) {
    synchronized {
      val blockManagerId = blockManagerAdded.blockManagerId
      val executorId = blockManagerId.executorId
      val maxMem = blockManagerAdded.maxMem
      val storageStatus = new StorageStatus(blockManagerId, maxMem)
      executorIdToStorageStatus(executorId) = storageStatus
    }
  }

  override def onBlockManagerRemoved(blockManagerRemoved: SparkListenerBlockManagerRemoved) {
    synchronized {
      val executorId = blockManagerRemoved.blockManagerId.executorId
      executorIdToStorageStatus.remove(executorId)
    }
  }

}
