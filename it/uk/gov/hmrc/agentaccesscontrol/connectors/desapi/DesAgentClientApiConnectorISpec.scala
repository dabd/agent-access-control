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

package uk.gov.hmrc.agentaccesscontrol.connectors.desapi

import com.kenshoo.play.metrics.MetricsRegistry
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{any, eq => eqs}
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.agentaccesscontrol.WSHttp
import uk.gov.hmrc.agentaccesscontrol.model.{DesAgentClientFlagsApiResponse, FoundResponse, NotFoundResponse}
import uk.gov.hmrc.agentaccesscontrol.support.WireMockWithOneAppPerSuiteISpec
import uk.gov.hmrc.domain.{AgentCode, SaAgentReference, SaUtr}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.MergedDataEvent
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext


class DesAgentClientApiConnectorISpec extends WireMockWithOneAppPerSuiteISpec with MockitoSugar {

  implicit val headerCarrier = HeaderCarrier()
  val agentCode = AgentCode("Agent")

  "getAgentClientRelationship" should {
    "request DES API with the correct auth tokens" in new Context {
      givenClientIsLoggedIn()
        .andIsRelatedToClientInDes(saUtr, "auth_token_33", "env_33").andAuthorisedByBoth648AndI648()

      val connectorWithDifferentHeaders = new DesAgentClientApiConnector(wiremockBaseUrl, "auth_token_33", "env_33", wsHttp)

      val response: DesAgentClientFlagsApiResponse = await(connectorWithDifferentHeaders.getAgentClientRelationship(saAgentReference, agentCode, saUtr))
      response shouldBe FoundResponse(auth64_8 = true, authI64_8 = true)
    }

    "pass along 64-8 and i64-8 information" when {
      "agent is authorised by 64-8 and i64-8" in new Context {
        givenClientIsLoggedIn()
          .andIsRelatedToClientInDes(saUtr).andAuthorisedByBoth648AndI648()

        when(mockAuditConnector.sendMergedEvent(any[MergedDataEvent])(eqs(headerCarrier), any[ExecutionContext])).thenThrow(new RuntimeException("EXCEPTION!"))

        await(connector.getAgentClientRelationship(saAgentReference, agentCode, saUtr)) shouldBe FoundResponse(auth64_8 = true, authI64_8 = true)
        outboundCallToDesShouldBeAudited(auth64_8 = true, authI64_8 = true)
      }
      "agent is authorised by only i64-8" in new Context {
        givenClientIsLoggedIn()
          .andIsRelatedToClientInDes(saUtr).andIsAuthorisedByOnlyI648()

        await(connector.getAgentClientRelationship(saAgentReference, agentCode, saUtr)) shouldBe FoundResponse(auth64_8 = false, authI64_8 = true)
        outboundCallToDesShouldBeAudited(auth64_8 = false, authI64_8 = true)
      }
      "agent is authorised by only 64-8" in new Context {
        givenClientIsLoggedIn()
          .andIsRelatedToClientInDes(saUtr).andIsAuthorisedByOnly648()

        await(connector.getAgentClientRelationship(saAgentReference, agentCode, saUtr)) shouldBe FoundResponse(auth64_8 = true, authI64_8 = false)
        outboundCallToDesShouldBeAudited(auth64_8 = true, authI64_8 = false)
      }
      "agent is not authorised" in new Context {
        givenClientIsLoggedIn()
          .andIsRelatedToClientInDes(saUtr).butIsNotAuthorised()

        await(connector.getAgentClientRelationship(saAgentReference, agentCode, saUtr)) shouldBe FoundResponse(auth64_8 = false, authI64_8 = false)
        outboundCallToDesShouldBeAudited(auth64_8 = false, authI64_8 = false)
      }
    }

    "return NotFoundResponse in case of a 404" in new Context {
      givenClientIsLoggedIn()
        .andHasNoRelationInDesWith(saUtr)

      await(connector.getAgentClientRelationship(saAgentReference, agentCode, saUtr)) shouldBe NotFoundResponse
    }

    "fail in any other cases, like internal server error" in new Context {
      givenClientIsLoggedIn().andDesIsDown()

      an[Exception] should be thrownBy await(connector.getAgentClientRelationship(saAgentReference, agentCode, saUtr))
    }

    "Metrics are logged for the outbound call" in new Context {
      val metricsRegistry = MetricsRegistry.defaultRegistry
      givenClientIsLoggedIn()
        .andIsRelatedToClientInDes(saUtr).andAuthorisedByBoth648AndI648()

      await(connector.getAgentClientRelationship(saAgentReference, agentCode, saUtr)) shouldBe FoundResponse(auth64_8 = true, authI64_8 = true)
      metricsRegistry.getTimers.get("Timer-ConsumedAPI-DES-GetAgentClientRelationship-GET").getCount should be >= 1L
    }
  }

  private abstract class Context extends Eventually {
    val mockAuditConnector = mock[AuditConnector]
    val wsHttp = new WSHttp {
      override def auditConnector = mockAuditConnector
    }

    val connector = new DesAgentClientApiConnector(wiremockBaseUrl, "secret", "test", wsHttp)
    val saAgentReference = SaAgentReference("AGENTR")
    val saUtr = SaUtr("SAUTR456")

    def givenClientIsLoggedIn() =
      given()
        .agentAdmin("ABCDEF122345").isLoggedIn()
        .andHasSaAgentReferenceWithEnrolment(saAgentReference)

    def outboundCallToDesShouldBeAudited(auth64_8: Boolean, authI64_8: Boolean): Unit = {
      // HttpAuditing.AuditingHook does the auditing asynchronously, so we need
      // to use eventually to avoid a race condition in this test
      eventually {
        val captor = ArgumentCaptor.forClass(classOf[MergedDataEvent])
        verify(mockAuditConnector).sendMergedEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])
        val event: MergedDataEvent = captor.getValue

        event.auditType shouldBe "OutboundCall"

        event.request.tags("path") shouldBe s"$wiremockBaseUrl/sa/agents/$saAgentReference/client/$saUtr"

        val responseJson = Json.parse(event.response.detail("responseMessage"))
        (responseJson \ "Auth_64-8").as[Boolean] shouldBe auth64_8
        (responseJson \ "Auth_i64-8").as[Boolean] shouldBe authI64_8
      }
    }
  }
}
