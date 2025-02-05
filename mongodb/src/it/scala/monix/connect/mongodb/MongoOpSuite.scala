/*
 * Copyright (c) 2020-2020 by The Monix Connect Project Developers.
 * See the project homepage at: https://monix.io
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

package monix.connect.mongodb

import org.scalatest.flatspec.AnyFlatSpecLike
import monix.execution.Scheduler.Implicits.global
import com.mongodb.client.model.{Collation, CollationCaseFirst, DeleteOptions, Filters, Updates}
import org.bson.conversions.Bson
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers

class MongoOpSuite extends AnyFlatSpecLike with Fixture with Matchers with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    MongoDb.dropDatabase(db).runSyncUnsafe()
    MongoDb.dropCollection(db, collectionName).runSyncUnsafe()
  }

  s"${MongoOp}" should "delete one single element by filtering" in {
    //given
    val exampleName = "deleteOneExample"
    val employees = genEmployeesWith(name = Some(exampleName)).sample.get
    MongoOp.insertMany(col, employees).runSyncUnsafe()

    //when
    val r = MongoOp.deleteOne(col, docNameFilter(exampleName)).runSyncUnsafe()

    //then
    val finalElements = MongoSource.count(col, docNameFilter(exampleName)).runSyncUnsafe()
    r.deleteCount shouldBe 1L
    r.wasAcknowledged shouldBe true
    finalElements shouldBe employees.size - 1
  }

  it should "delete one single element when it does not exists" in {
    //given
    val filter = Filters.eq("name", "deleteWhenNoExists")

    //when
    val r = MongoOp.deleteOne(col, filter).runSyncUnsafe()

    //then
    val finalElements = MongoSource.countAll(col).runSyncUnsafe()
    r.deleteCount shouldBe 0L
    r.wasAcknowledged shouldBe true
    finalElements shouldBe 0
  }

  it should "delete one single element with delete options" in {
    //given
    val uppercaseNat = "Japanese"
    val lowercaseNat = "japanese"
    val collation = Collation.builder().collationCaseFirst(CollationCaseFirst.UPPER).locale("es").build()
    val deleteOptions = new DeleteOptions().collation(collation)
    val employee = genEmployeeWith(city = Some(uppercaseNat)).sample.get
    val employees = genEmployeesWith(city = Some(lowercaseNat)).sample.get
    MongoOp.insertOne(col, employee).runSyncUnsafe()
    MongoOp.insertMany(col, employees).runSyncUnsafe()

    //when
    val r = MongoOp.deleteOne(col, Filters.in("city", lowercaseNat, uppercaseNat), deleteOptions).runSyncUnsafe()

    //then
    val nUppercaseNat = MongoSource.count(col, Filters.eq("city", uppercaseNat)).runSyncUnsafe()
    r.deleteCount shouldBe 1L
    r.wasAcknowledged shouldBe true
    nUppercaseNat shouldBe 0L
  }

  it should "delete many elements by filter" in { //todo
    //given
    val exampleName = "deleteManyExample"
    val employees = genEmployeesWith(name = Some(exampleName)).sample.get
    MongoOp.insertMany(col, employees).runSyncUnsafe()

    //when
    val r = MongoOp.deleteMany(col, Filters.eq("name", exampleName)).runSyncUnsafe()

    //then
    val finalElements = MongoSource.count(col, docNameFilter(exampleName)).runSyncUnsafe()
    r.deleteCount shouldBe employees.length
    r.wasAcknowledged shouldBe true
    finalElements shouldBe 0L
  }

  s"${MongoOp}.insertOne"  should "insert one single element" in {
    //given
    val e = genEmployee.sample.get

    //when
    val r = MongoOp.insertOne(col, e).runSyncUnsafe()

    //then
    r.insertedId.isDefined shouldBe true
    r.wasAcknowledged shouldBe true

    //and
    MongoSource.findAll(col).headL.runSyncUnsafe() shouldBe e
  }

  it should "insert many elements" in {
    //given
    val nationality = "Vancouver"
    val employees = genEmployeesWith(city = Some(nationality)).sample.get

    //when
    val r = MongoOp.insertMany(col, employees).runSyncUnsafe()

    //then
    r.insertedIds.size shouldBe employees.size
    r.wasAcknowledged shouldBe true

    //and
    val elements = MongoSource.findAll(col).toListL.runSyncUnsafe()
    elements shouldBe employees
  }

  it should "replace one single element" in {
    //given
    val employeeName = "Humberto"
    val e = genEmployeeWith(name = Option(employeeName)).sample.get
    val filter: Bson = Filters.eq("name", employeeName)

    //and
    MongoOp.insertOne(col, e).runSyncUnsafe()

    //when
    val r = MongoOp.replaceOne(col, filter, e.copy(age = e.age + 1)).runSyncUnsafe()

    //then
    r.modifiedCount shouldBe 1L

    //and
    val updated = MongoSource.find(col, filter).headL.runSyncUnsafe()
    updated.age shouldBe e.age + 1
  }

  it should "update one single element" in {
    //given
    val cambridge = "Cambridge"
    val oxford = "Oxford"
    val employees = genEmployeesWith(city = Some(cambridge)).sample.get
    MongoOp.insertMany(col, employees).runSyncUnsafe()

    //when
    val updateResult = MongoOp.updateOne(col, nationalityDocument(cambridge), Updates.set("city", oxford)).runSyncUnsafe()

    //then
    val cambridgeEmployeesCount = MongoSource.count(col, nationalityDocument(cambridge)).runSyncUnsafe()
    val oxfordEmployeesCount = MongoSource.count(col, nationalityDocument(oxford)).runSyncUnsafe()
    cambridgeEmployeesCount shouldBe employees.size - 1
    oxfordEmployeesCount shouldBe 1
    updateResult.matchedCount shouldBe 1
    updateResult.modifiedCount shouldBe 1
    updateResult.wasAcknowledged shouldBe true
  }

  it should  "update one single element list" in {
    //given
    val employee = genEmployeeWith(city = Some("Galway"), activities = List("Cricket")).sample.get
    MongoOp.insertOne(col, employee).runSyncUnsafe()

    //when
    val filter = Filters.eq("city", "Galway")
    val update = Updates.push("activities", "Ping Pong")
    val updateResult = MongoOp.updateOne(col, filter, update).runSyncUnsafe()

    //then
    val r = MongoSource.find(col, filter).headL.runSyncUnsafe()
    r.activities.contains("Ping Pong") shouldBe true
    updateResult.matchedCount shouldBe 1
    updateResult.modifiedCount shouldBe 1
    updateResult.wasAcknowledged shouldBe true
  }

  it should "update many elements" in {
    //given
    val bogota = "Bogota"
    val rio = "Rio"
    val employees = genEmployeesWith(city = Some(bogota)).sample.get
    MongoOp.insertMany(col, employees).runSyncUnsafe()

    //when
    val updateResult = MongoOp.updateMany(col, nationalityDocument(bogota), Updates.set("city", rio)).runSyncUnsafe()

    //then
    updateResult.matchedCount shouldBe employees.size
    updateResult.modifiedCount shouldBe employees.size
    updateResult.wasAcknowledged shouldBe true

    //and
    val colombians = MongoSource.count(col, nationalityDocument(bogota)).runSyncUnsafe()
    val brazilians = MongoSource.count(col, nationalityDocument(rio)).runSyncUnsafe()
    colombians shouldBe 0
    brazilians shouldBe employees.size
  }

}
