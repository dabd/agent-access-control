/*
 * Copyright 2017 HM Revenue & Customs
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

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.agentaccesscontrol.audit.AgentAccessControlEvent.AgentAccessControlDecision
import uk.gov.hmrc.agentaccesscontrol.audit.AuditService
import uk.gov.hmrc.agentaccesscontrol.connectors.mtd.{AgenciesConnector, AgencyRecord, Relationship, RelationshipsConnector}
import uk.gov.hmrc.agentaccesscontrol.model.{Arn, MtdClientId}
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class MtdAuthorisationServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  val agenciesConnector = mock[AgenciesConnector]
  val relationshipsConnector = mock[RelationshipsConnector]
  val auditService = mock[AuditService]

  val service = new MtdAuthorisationService(agenciesConnector, relationshipsConnector, auditService)

  val agentCode = AgentCode("agentCode")
  val arn = Arn("arn")
  val clientId = MtdClientId("clientId")
  implicit val hc = HeaderCarrier()
  implicit val fakeRequest = FakeRequest("GET", "/agent-access-control/mtd-sa-auth/agent/arn/client/utr")

  "authoriseForSa" should {
    "allow access for agent with a client relationship" in {
      whenAgenciesConnectorIsCalled thenReturn Some(AgencyRecord(arn))
      whenRelationshipsConnectorIsCalled thenReturn Some(Relationship(arn.value, clientId.value))

      val result = await(service.authoriseForSa(agentCode, clientId))

      result shouldBe true
    }

    "deny access for a non-mtd agent" in {
      whenAgenciesConnectorIsCalled thenReturn None

      val result = await(service.authoriseForSa(agentCode, clientId))

      result shouldBe false
      verify(relationshipsConnector, never).fetchRelationship(any[Arn], any[MtdClientId])(any[ExecutionContext], any[HeaderCarrier])
    }

    "deny access for a mtd agent without a client relationship" in {
      whenAgenciesConnectorIsCalled thenReturn Some(AgencyRecord(arn))
      whenRelationshipsConnectorIsCalled thenReturn None

      val result = await(service.authoriseForSa(agentCode, clientId))

      result shouldBe false
    }

    "audit appropriate values" when {
      "decision is made to allow access" in {
        whenAgenciesConnectorIsCalled thenReturn Some(AgencyRecord(arn))
        whenRelationshipsConnectorIsCalled thenReturn Some(Relationship(arn.value, clientId.value))

        await(service.authoriseForSa(agentCode, clientId))

        verify(auditService)
          .auditEvent(AgentAccessControlDecision, "agent access decision", agentCode, "mtd-sa", clientId, Seq("accessGranted" -> true, "arn" -> arn))(hc, fakeRequest)
      }

      "decision is made to deny access" in {
        whenAgenciesConnectorIsCalled thenReturn Some(AgencyRecord(arn))
        whenRelationshipsConnectorIsCalled thenReturn None

        await(service.authoriseForSa(agentCode, clientId))

        verify(auditService)
          .auditEvent(AgentAccessControlDecision, "agent access decision", agentCode, "mtd-sa", clientId, Seq("accessGranted" -> false, "arn" -> arn))(hc, fakeRequest)
      }

      "no agent record exists" in {
        whenAgenciesConnectorIsCalled thenReturn None

        await(service.authoriseForSa(agentCode, clientId))

        verify(auditService)
          .auditEvent(AgentAccessControlDecision, "agent access decision", agentCode, "mtd-sa", clientId, Seq("accessGranted" -> false))(hc, fakeRequest)
      }
    }
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(agenciesConnector, relationshipsConnector, auditService)
  }

  def whenRelationshipsConnectorIsCalled =
    when(relationshipsConnector.fetchRelationship(any[Arn], any[MtdClientId])(any[ExecutionContext], any[HeaderCarrier]))

  def whenAgenciesConnectorIsCalled =
    when(agenciesConnector.fetchAgencyRecord(any[AgentCode])(any[ExecutionContext], any[HeaderCarrier]))
}
