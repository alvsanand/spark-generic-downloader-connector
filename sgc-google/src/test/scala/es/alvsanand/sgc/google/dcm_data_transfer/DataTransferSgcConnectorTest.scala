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

package es.alvsanand.sgc.google.dcm_data_transfer

import java.io.{ByteArrayOutputStream, OutputStream}
import java.nio.file.{Files, Paths}

import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.{Objects, StorageObject}
import es.alvsanand.sgc.core.util.ReflectionUtils._
import es.alvsanand.sgc.google
import es.alvsanand.sgc.google.dcm_data_transfer.DataTransferFileTypes.DataTransferFileType
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest._

import scala.collection.JavaConversions._

class DataTransferSgcConnectorTest extends FlatSpec with Matchers with OptionValues with Inside
  with
  Inspectors with BeforeAndAfterAll {

  private def createConnectorList(files: List[String],
                                   types: Seq[DataTransferFileType] = Seq.empty):
                                    DataTransferSgcConnector = {
    val connector = new DataTransferSgcConnector(DataTransferParameters("FOO", "FOO", types))

    val mockBuilder: Storage = Mockito.mock(classOf[Storage])

    Mockito.doAnswer(new Answer[Any]() {
      def answer(invocation: InvocationOnMock): mockBuilder.Objects = {
        val arg1: Array[AnyRef] = invocation.getArguments

        val mockObjects = Mockito.mock(classOf[mockBuilder.Objects])

        Mockito.doAnswer(new Answer[Any]() {
          def answer(invocation: InvocationOnMock): mockObjects.List = {
            val arg2: Array[AnyRef] = invocation.getArguments

            val mockList = Mockito.mock(classOf[mockObjects.List])

            Mockito.doAnswer(new Answer[Any]() {
              def answer(invocation: InvocationOnMock): Objects = {
                val arg3: Array[AnyRef] = invocation.getArguments

                val objects = new com.google.api.services.storage.model.Objects

                objects.setItems(files.map(f => {
                  val so = new StorageObject()
                  so.setName(f)
                  so
                }))
              }
            }).when(mockList).execute()

            mockList
          }
        }).when(mockObjects).list(org.mockito.Matchers.anyString())

        mockObjects
      }
    }).when(mockBuilder).objects()

    connector.setV("client", mockBuilder)

    connector
  }

  private def createConnectorFile(file: String): DataTransferSgcConnector = {
    val connector = new DataTransferSgcConnector(DataTransferParameters("FOO", "FOO"))

    val mockBuilder: Storage = Mockito.mock(classOf[Storage])

    Mockito.doAnswer(new Answer[Any]() {
      def answer(invocation: InvocationOnMock): mockBuilder.Objects = {
        val arg1: Array[AnyRef] = invocation.getArguments

        val mockObjects = Mockito.mock(classOf[mockBuilder.Objects])

        Mockito.doAnswer(new Answer[Any]() {
          def answer(invocation: InvocationOnMock): mockObjects.Get = {
            val arg2: Array[AnyRef] = invocation.getArguments

            val mockGet = Mockito.mock(classOf[mockObjects.Get])

            Mockito.doAnswer(new Answer[Any]() {
              def answer(invocation: InvocationOnMock): Any = {
                val arg3: Array[AnyRef] = invocation.getArguments

                if (file == null) {
                  throw new Exception()
                }

                val out = arg3(0).asInstanceOf[java.io.OutputStream]

                val in = getClass.getResourceAsStream(file)
                out.write(
                  Stream.continually(in.read).takeWhile(-1 !=).map(_.toByte).toArray
                )
                out.close()
              }
            }).when(mockGet).executeMediaAndDownloadTo(org.mockito.Matchers.any[OutputStream]())

            mockGet
          }
        }).when(mockObjects).get(org.mockito.Matchers.anyString(), org.mockito.Matchers.anyString())

        mockObjects
      }
    }).when(mockBuilder).objects()

    connector.setV("client", mockBuilder)

    connector
  }

  it should "fail with empty parameters" in {
    a[IllegalArgumentException] shouldBe
      thrownBy(new DataTransferSgcConnector(DataTransferParameters("FOO", null)))
    a[IllegalArgumentException] shouldBe
      thrownBy(new DataTransferSgcConnector(DataTransferParameters("FOO", "")))
    a[IllegalArgumentException] shouldBe
      thrownBy(new DataTransferSgcConnector(DataTransferParameters(null, "FOO")))
    a[IllegalArgumentException] shouldBe
      thrownBy(new DataTransferSgcConnector(DataTransferParameters("", "FOO")))
  }

  it should "work with empty list" in {
    val connector = createConnectorList(List[String]())

    connector.list() should be(List[String]())
  }

  it should "work with simple list" in {
    val connector = createConnectorList(List[String]
      ("dcm_account411205_activity_20160927_20160928_050546_293409263" +
        ".csv.gz",
        "dcm_account411205_click_2016092602_20160926_133141_292763957.csv.gz",
        "dcm_account411205_impression_2016092603_20160926_131805_292768332.csv.gz",
        "AAA_BBB_CCC"))

    connector.list() should be(List[DataTransferSlot](google.dcm_data_transfer
      .DataTransferFileTypes.getDataTransferFile
    ("dcm_account411205_activity_20160927_20160928_050546_293409263.csv.gz").get,
      google.dcm_data_transfer.DataTransferFileTypes
        .getDataTransferFile("dcm_account411205_click_2016092602_20160926_133141_292763957.csv" +
          ".gz").get,
      google.dcm_data_transfer.DataTransferFileTypes
        .getDataTransferFile("dcm_account411205_impression_2016092603_20160926_131805_292768332" +
          ".csv.gz")
        .get))
  }

  it should "work with filter" in {
    val connector = createConnectorList(List[String]
      ("dcm_account411205_activity_20160927_20160928_050546_293409263" +
        ".csv.gz",
        "dcm_account411205_click_2016092602_20160926_133141_292763957.csv.gz",
        "dcm_account411205_impression_2016092603_20160926_131805_292768332.csv.gz",
        "AAA_BBB_CCC"), Seq[DataTransferFileType](DataTransferFileTypes.ACTIVITY,
      DataTransferFileTypes.IMPRESSION))

    connector.list() should be(List[DataTransferSlot](google.dcm_data_transfer
      .DataTransferFileTypes.getDataTransferFile
    ("dcm_account411205_activity_20160927_20160928_050546_293409263.csv.gz").get,
      google.dcm_data_transfer.DataTransferFileTypes
        .getDataTransferFile("dcm_account411205_impression_2016092603_20160926_131805_292768332" +
          ".csv.gz")
        .get))
  }

  it should "work with existing name" in {
    val connector = createConnectorFile("/sample_files/sampleFile.txt")

    val data = Files.readAllBytes(Paths.get(getClass.getResource("/sample_files/sampleFile" +
      ".txt").getFile))

    val out: ByteArrayOutputStream = new ByteArrayOutputStream

    connector.fetch(DataTransferSlot("log4j_test.xml", null), out)

    out.toByteArray should be(data)
  }

  it should "fail with bad name" in {
    val connector = createConnectorFile(null)

    val out: ByteArrayOutputStream = new ByteArrayOutputStream

    intercept[Throwable] {
      connector.fetch(DataTransferSlot("log4j_test.xml", null), out)
    }
  }
}
