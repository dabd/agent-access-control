/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.agentaccesscontrol

import org.scalatest.{Matchers, fixture}
import org.scalatestplus.play.MixedFixtures
import play.api.test.Helpers._
import play.api.test.{FakeApplication, FakeRequest}
import play.mvc.Http.Status
import uk.gov.hmrc.agentaccesscontrol.controllers.AuthorisationService
import uk.gov.hmrc.agentaccesscontrol.model.EmpRef
import uk.gov.hmrc.domain.AgentCode

import scala.concurrent.Future


class AuthorisationControllerISpec extends fixture.WordSpecLike with Matchers with MixedFixtures {

  val suppressAuthFilterConfiguration: Map[String, Boolean] = Map(
    "controllers.uk.gov.hmrc.agentaccesscontrol.controllers.AuthorisationController.needsAuth" -> false
  )

  class TestMicroserviceGlobal(isAuthorisedToReturn: Boolean) extends MicroserviceGlobal {
    override val authorisationService: AuthorisationService = new AuthorisationService {
      override def isAuthorised = Future successful isAuthorisedToReturn
    }
  }

  "isAuthorised" should {

    "return 401 if the AuthorisationService doesn't permit access" in new App(FakeApplication(
      additionalConfiguration = suppressAuthFilterConfiguration,
      withGlobal = Some(new TestMicroserviceGlobal(false))
    )) {
      val Some(result) = route(FakeRequest(controllers.routes.AuthorisationController.isAuthorised(AgentCode("AGENTCODE"), EmpRef("000", "A1B2C4D5"))))
      status(result) shouldBe Status.UNAUTHORIZED
    }


    "return 200 if the AuthorisationService allows access" in new App(FakeApplication(
      additionalConfiguration = suppressAuthFilterConfiguration,
      withGlobal = Some(new TestMicroserviceGlobal(true))
    )) {
      // TODO decide whether to test using the reverse router or by explicitly passing in a fixed URL
      //      val Some(result) = route(FakeRequest(GET, "/sa-auth/agent/AGENTCODE/client/000%2F12345"))
      val Some(result) = route(FakeRequest(controllers.routes.AuthorisationController.isAuthorised(AgentCode("AGENTCODE"), EmpRef("000", "A1B2C4D5"))))
      status(result) shouldBe Status.OK
    }
  }
}