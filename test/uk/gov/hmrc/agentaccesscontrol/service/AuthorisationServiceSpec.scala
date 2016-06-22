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

package uk.gov.hmrc.agentaccesscontrol.service

import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.agentaccesscontrol.connectors.AuthConnector
import uk.gov.hmrc.agentaccesscontrol.connectors.desapi.DesAgentClientApiConnector
import uk.gov.hmrc.agentaccesscontrol.model.{FoundResponse, NotFoundResponse}
import uk.gov.hmrc.domain.{AgentCode, SaAgentReference, SaUtr}
import uk.gov.hmrc.play.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future


class AuthorisationServiceSpec extends UnitSpec with MockitoSugar {
  val agentCode = AgentCode("ABCDEF123456")
  val saAgentRef = SaAgentReference("ABC456")
  val clientSaUtr = SaUtr("CLIENTSAUTR456")


  implicit val headerCarrier = HeaderCarrier()

  "isAuthorised" should {
    "return false if the Agent or the relationship between the Agent and Client was not found" in new Context {
      when(mockAuthConnector.currentSaAgentReference()).thenReturn(Some(saAgentRef))
      when(mockDesAgentClientApiConnector.getAgentClientRelationship(saAgentRef, clientSaUtr)).
        thenReturn(NotFoundResponse)

      await(authorisationService.isAuthorised(agentCode, clientSaUtr)) shouldBe false
    }

    "return true if the API returns 64-8=true and i64-8=true" in new Context {
      when(mockAuthConnector.currentSaAgentReference()).thenReturn(Some(saAgentRef))
      when(mockDesAgentClientApiConnector.getAgentClientRelationship(saAgentRef, clientSaUtr)).
        thenReturn(FoundResponse(auth64_8 = true, authI64_8 = true))

      await(authorisationService.isAuthorised(agentCode, clientSaUtr)) shouldBe true
    }

    "return false if the API returns 64-8=true and i64-8=false" in new Context {
      when(mockAuthConnector.currentSaAgentReference()).thenReturn(Some(saAgentRef))
      when(mockDesAgentClientApiConnector.getAgentClientRelationship(saAgentRef, clientSaUtr)).
        thenReturn(FoundResponse(auth64_8 = true, authI64_8 = false))

      await(authorisationService.isAuthorised(agentCode, clientSaUtr)) shouldBe false
    }

    "return false if the API returns 64-8=false and i64-8=true" in new Context {
      when(mockAuthConnector.currentSaAgentReference()).thenReturn(Some(saAgentRef))
      when(mockDesAgentClientApiConnector.getAgentClientRelationship(saAgentRef, clientSaUtr)).
        thenReturn(FoundResponse(auth64_8 = false, authI64_8 = true))

      await(authorisationService.isAuthorised(agentCode, clientSaUtr)) shouldBe false
    }

    "return false if the API returns 64-8=false and i64-8=false" in new Context {
      when(mockAuthConnector.currentSaAgentReference()).thenReturn(Some(saAgentRef))
      when(mockDesAgentClientApiConnector.getAgentClientRelationship(saAgentRef, clientSaUtr)).
        thenReturn(FoundResponse(auth64_8 = false, authI64_8 = false))

      await(authorisationService.isAuthorised(agentCode, clientSaUtr)) shouldBe false
    }

    "return false if SA agent reference cannot be found" in new Context {
      when(mockAuthConnector.currentSaAgentReference()).thenReturn(None)

      await(authorisationService.isAuthorised(agentCode, clientSaUtr)) shouldBe false
    }

    "propagate any errors that happened" in new Context {
      when(mockAuthConnector.currentSaAgentReference()).thenReturn(Some(saAgentRef))
      when(mockDesAgentClientApiConnector.getAgentClientRelationship(saAgentRef, clientSaUtr)).
        thenReturn(Future failed new BadRequestException("bad request"))

      intercept[BadRequestException] {
        await(authorisationService.isAuthorised(agentCode, clientSaUtr))
      }
    }
  }

  private abstract class Context {
    val mockDesAgentClientApiConnector = mock[DesAgentClientApiConnector]
    val mockAuthConnector = mock[AuthConnector]
    val authorisationService = new AuthorisationService(
      mockDesAgentClientApiConnector,
      mockAuthConnector)
  }
}
