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
import play.api.test.FakeRequest
import uk.gov.hmrc.agentaccesscontrol.audit.AgentAccessControlEvent.AgentAccessControlDecision
import uk.gov.hmrc.agentaccesscontrol.audit.AuditService
import uk.gov.hmrc.agentaccesscontrol.connectors.{AuthConnector, AuthDetails}
import uk.gov.hmrc.domain.{AgentCode, SaAgentReference, SaUtr}
import uk.gov.hmrc.play.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future


class AuthorisationServiceSpec extends UnitSpec with MockitoSugar {
  val agentCode = AgentCode("ABCDEF123456")
  val saAgentRef = SaAgentReference("ABC456")
  val clientSaUtr = SaUtr("CLIENTSAUTR456")


  implicit val hc = HeaderCarrier()
  implicit val fakeRequest = FakeRequest("GET", s"/agent-access-control/sa-auth/agent/$agentCode/client/$clientSaUtr")


  "isAuthorised" should {
    "return false if SA agent reference cannot be found (as CESA cannot be checked)" in new Context {
      when(mockAuthConnector.currentAuthDetails()).thenReturn(Some(AuthDetails(None, "ggId", affinityGroup = Some("Agent"), agentUserRole = Some("admin"))))

      await(authorisationService.isAuthorised(agentCode, clientSaUtr)) shouldBe false
      verify(mockAuditService).auditSaEvent(AgentAccessControlDecision, "agent access decision", agentCode, clientSaUtr,
        Seq("credId" -> "ggId", "accessGranted" -> false, "affinityGroup" -> "Agent", "agentUserRole" -> "admin"))(hc, fakeRequest)
    }

    "return false if SA agent reference is found and CesaAuthorisationService returns false and GG Authorisation returns true" in new Context {
      when(mockAuthConnector.currentAuthDetails()).thenReturn(Some(AuthDetails(Some(saAgentRef), "ggId", affinityGroup = Some("Agent"), agentUserRole = Some("admin"))))
      when(mockGGAuthorisationService.isAuthorisedForSaInGovernmentGateway(agentCode, "ggId", clientSaUtr)).thenReturn(true)
      when(mockDesAuthorisationService.isAuthorisedInCesa(agentCode, saAgentRef, clientSaUtr))
        .thenReturn(false)

      await(authorisationService.isAuthorised(agentCode, clientSaUtr)) shouldBe false
      verify(mockAuditService).auditSaEvent(AgentAccessControlDecision, "agent access decision", agentCode, clientSaUtr,
        Seq("credId" -> "ggId", "accessGranted" -> false, "cesaResult" -> false, "gatewayResult" -> true, "saAgentReference" -> saAgentRef, "affinityGroup" -> "Agent", "agentUserRole" -> "admin"))(hc, fakeRequest)
    }

    "return true if SA agent reference is found and DesAuthorisationService returns true and GG Authorisation returns true" in new Context {
      when(mockAuthConnector.currentAuthDetails()).thenReturn(Some(AuthDetails(Some(saAgentRef), "ggId", affinityGroup = Some("Agent"), agentUserRole = Some("admin"))))
      when(mockGGAuthorisationService.isAuthorisedForSaInGovernmentGateway(agentCode, "ggId", clientSaUtr)).thenReturn(true)
      when(mockDesAuthorisationService.isAuthorisedInCesa(agentCode, saAgentRef, clientSaUtr))
        .thenReturn(true)

      await(authorisationService.isAuthorised(agentCode, clientSaUtr)) shouldBe true
      verify(mockAuditService).auditSaEvent(AgentAccessControlDecision, "agent access decision", agentCode, clientSaUtr,
        Seq("credId" -> "ggId", "accessGranted" -> true, "cesaResult" -> true, "gatewayResult" -> true, "saAgentReference" -> saAgentRef, "affinityGroup" -> "Agent", "agentUserRole" -> "admin"))(hc, fakeRequest)
    }

    "not hard code audited values" in new Context {
      val differentSaAgentRef = SaAgentReference("XYZ123")

      when(mockAuthConnector.currentAuthDetails()).thenReturn(Some(AuthDetails(Some(differentSaAgentRef), "ggId", affinityGroup = Some("Organisation"), agentUserRole = Some("assistant"))))
      when(mockGGAuthorisationService.isAuthorisedForSaInGovernmentGateway(agentCode, "ggId", clientSaUtr)).thenReturn(true)
      when(mockDesAuthorisationService.isAuthorisedInCesa(agentCode, differentSaAgentRef, clientSaUtr))
        .thenReturn(true)

      await(authorisationService.isAuthorised(agentCode, clientSaUtr)) shouldBe true
      verify(mockAuditService).auditSaEvent(AgentAccessControlDecision, "agent access decision", agentCode, clientSaUtr,
        Seq("credId" -> "ggId", "accessGranted" -> true, "cesaResult" -> true, "gatewayResult" -> true, "saAgentReference" -> differentSaAgentRef, "affinityGroup" -> "Organisation", "agentUserRole" -> "assistant"))(hc, fakeRequest)
    }

    "still work if the fields only used for auditing are removed from the auth record" in new Context {
      when(mockAuthConnector.currentAuthDetails()).thenReturn(Some(AuthDetails(Some(saAgentRef), "ggId", affinityGroup = None, agentUserRole = None)))
      when(mockGGAuthorisationService.isAuthorisedForSaInGovernmentGateway(agentCode, "ggId", clientSaUtr)).thenReturn(true)
      when(mockDesAuthorisationService.isAuthorisedInCesa(agentCode, saAgentRef, clientSaUtr))
        .thenReturn(true)

      await(authorisationService.isAuthorised(agentCode, clientSaUtr)) shouldBe true
      verify(mockAuditService).auditSaEvent(AgentAccessControlDecision, "agent access decision", agentCode, clientSaUtr,
        Seq("credId" -> "ggId", "accessGranted" -> true, "cesaResult" -> true, "gatewayResult" -> true, "saAgentReference" -> saAgentRef))(hc, fakeRequest)
    }

    "return false if SA agent reference is found and DesAuthorisationService returns true and GG Authorisation returns false" in new Context {
      when(mockAuthConnector.currentAuthDetails()).thenReturn(Some(AuthDetails(Some(saAgentRef), "ggId", affinityGroup = Some("Agent"), agentUserRole = Some("admin"))))
      when(mockGGAuthorisationService.isAuthorisedForSaInGovernmentGateway(agentCode, "ggId", clientSaUtr)).thenReturn(false)
      when(mockDesAuthorisationService.isAuthorisedInCesa(agentCode, saAgentRef, clientSaUtr))
        .thenReturn(true)

      await(authorisationService.isAuthorised(agentCode, clientSaUtr)) shouldBe false
      verify(mockAuditService).auditSaEvent(AgentAccessControlDecision, "agent access decision", agentCode, clientSaUtr,
        Seq("credId" -> "ggId", "accessGranted" -> false, "cesaResult" -> true, "gatewayResult" -> false, "saAgentReference" -> saAgentRef, "affinityGroup" -> "Agent", "agentUserRole" -> "admin"))(hc, fakeRequest)
    }

    "return false if SA agent reference is found and DesAuthorisationService returns false and GG Authorisation returns false" in new Context {
      when(mockAuthConnector.currentAuthDetails()).thenReturn(Some(AuthDetails(Some(saAgentRef), "ggId", affinityGroup = Some("Agent"), agentUserRole = Some("admin"))))
      when(mockGGAuthorisationService.isAuthorisedForSaInGovernmentGateway(agentCode, "ggId", clientSaUtr)).thenReturn(false)
      when(mockDesAuthorisationService.isAuthorisedInCesa(agentCode, saAgentRef, clientSaUtr))
        .thenReturn(false)

      await(authorisationService.isAuthorised(agentCode, clientSaUtr)) shouldBe false
      verify(mockAuditService).auditSaEvent(AgentAccessControlDecision, "agent access decision", agentCode, clientSaUtr,
        Seq("credId" -> "ggId", "accessGranted" -> false, "cesaResult" -> false, "gatewayResult" -> false, "saAgentReference" -> saAgentRef, "affinityGroup" -> "Agent", "agentUserRole" -> "admin"))(hc, fakeRequest)
    }

    "return false if user is not logged in" in new Context {
      when(mockAuthConnector.currentAuthDetails()).thenReturn(None)
      await(authorisationService.isAuthorised(agentCode, clientSaUtr)) shouldBe false
    }

    "propagate any errors that happened" in new Context {
      when(mockAuthConnector.currentAuthDetails()).thenReturn(Future failed new BadRequestException("bad request"))

      intercept[BadRequestException] {
        await(authorisationService.isAuthorised(agentCode, clientSaUtr))
      }
    }
  }

  private abstract class Context {
    val mockAuthConnector = mock[AuthConnector]
    val mockDesAuthorisationService = mock[DesAuthorisationService]
    val mockGGAuthorisationService = mock[GovernmentGatewayAuthorisationService]
    val mockAuditService = mock[AuditService]
    val authorisationService = new AuthorisationService(
      mockDesAuthorisationService,
      mockAuthConnector,
      mockGGAuthorisationService,
      mockAuditService)
  }
}
