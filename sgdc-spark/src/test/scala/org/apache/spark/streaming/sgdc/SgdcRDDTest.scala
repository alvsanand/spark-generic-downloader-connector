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

package org.apache.spark.streaming.sgdc

import java.util.Date

import es.alvsanand.sgdc.core.downloader.{SgdcDownloaderParameters, SgdcSlot}
import es.alvsanand.sgdc.core.util.{SgdcDownloaderFactoryHelper, SparkTest}

class SgdcRDDTest extends SparkTest {
  it should "return one partition" in {
    val slot = SgdcSlot("/files/example.txt")

    val rdd = new SgdcRDD(sc, Array(slot), SgdcDownloaderFactoryHelper.createFactory(Seq
    (slot)), SgdcDownloaderParameters())

    rdd.partitions should be(Array(new SgdcRDDPartition(slot, 0)))
  }

  it should "return three partition" in {
    val slots = Array(
      SgdcSlot("/files/example.txt"),
      SgdcSlot("/files/example_20161201.txt"),
      SgdcSlot("/files/example_20161202.txt"))

    val rdd = new SgdcRDD(sc, slots, SgdcDownloaderFactoryHelper.createFactory(slots),
      SgdcDownloaderParameters())
    val partitions = rdd.partitions

    partitions.size should be(3)
    partitions(0).asInstanceOf[SgdcRDDPartition[SgdcSlot]].slot should be(slots(0))
    partitions(0).asInstanceOf[SgdcRDDPartition[SgdcSlot]].index should be(0)
    partitions(1).asInstanceOf[SgdcRDDPartition[SgdcSlot]].slot should be(slots(1))
    partitions(1).asInstanceOf[SgdcRDDPartition[SgdcSlot]].index should be(1)
    partitions(2).asInstanceOf[SgdcRDDPartition[SgdcSlot]].slot should be(slots(2))
    partitions(2).asInstanceOf[SgdcRDDPartition[SgdcSlot]].index should be(2)
  }

  it should "test simple File" in {
    val slot = SgdcSlot("/files/example.txt")

    val rdd = new SgdcRDD(sc, Array(slot), SgdcDownloaderFactoryHelper.createFactory(Seq
    (slot)), SgdcDownloaderParameters())

    rdd.count() should be(5)
    rdd.collect() should be(Array("LINE 001", "LINE 002", "LINE 003", "LINE 004", "LINE 005"))
  }

  it should "test multiple Files" in {
    val slots = Array(
      SgdcSlot("/files/example_20161201.txt"),
      SgdcSlot("/files/example_20161202.txt"))

    val rdd = new SgdcRDD(sc, slots, SgdcDownloaderFactoryHelper.createFactory(slots),
      SgdcDownloaderParameters())

    rdd.count() should be(10)
    rdd.collect() should be(Array("LINE 001 - 20161201", "LINE 002 - 20161201", "LINE 003 - " +
      "20161201", "LINE 004 - " +
      "20161201", "LINE 005 - 20161201",
      "LINE 001 - 20161202", "LINE 002 - 20161202", "LINE 003 - 20161202", "LINE 004 - 20161202",
      "LINE 005 - " +
        "20161202"))
  }

  it should "test simple File with enough retries" in {
    val slot = SgdcSlot("/files/example.txt")

    val rdd = new SgdcRDD(sc, Array(slot), SgdcDownloaderFactoryHelper.createFactory(Seq
    (slot),
      downloadBadTries = 2), SgdcDownloaderParameters(), maxRetries = 2)

    rdd.count() should be(5)
    rdd.collect() should be(Array("LINE 001", "LINE 002", "LINE 003", "LINE 004", "LINE 005"))
  }

  it should "test simple File with not enough retries" in {
    val slot = SgdcSlot("/files/example.txt")

    intercept[org.apache.spark.SparkException] {
      new SgdcRDD(sc, Array(slot), SgdcDownloaderFactoryHelper.createFactory(Seq(slot),
        downloadBadTries =
        2), SgdcDownloaderParameters(), maxRetries = 1).collect()
    }
  }

  it should "test bad File" in {
    val slot = SgdcSlot("BAD_FILE")

    intercept[org.apache.spark.SparkException] {
      new SgdcRDD(sc, Array(slot), SgdcDownloaderFactoryHelper.createFactory(Seq(slot)),
        SgdcDownloaderParameters()).collect()
    }
  }

  it should "test simple GZ File" in {
    val slot = SgdcSlot("/files/example.txt.gz")

    val rdd = new SgdcRDD(sc, Array(slot), SgdcDownloaderFactoryHelper.createFactory(Seq
    (slot)), SgdcDownloaderParameters())

    rdd.count() should be(5)
    rdd.collect() should be(Array("LINE 001", "LINE 002", "LINE 003", "LINE 004", "LINE 005"))
  }
}