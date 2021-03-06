package uk.gov.hmrc.agentaccesscontrol

import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.agentaccesscontrol.audit.AgentAccessControlEvent.AgentAccessControlDecision
import uk.gov.hmrc.agentaccesscontrol.model.{Arn, MtdClientId}
import uk.gov.hmrc.agentaccesscontrol.stubs.DataStreamStub
import uk.gov.hmrc.agentaccesscontrol.support.{Resource, WireMockWithOneServerPerSuiteISpec}
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.http.HttpResponse

class MtdAuthorisationISpec extends WireMockWithOneServerPerSuiteISpec {
  val agentCode = AgentCode("A11112222A")
  val arn = Arn("01234567890")
  val clientId = MtdClientId("12345677890")

  "/agent-access-control/sa-auth/agent/:agentCode/client/:mtdSaClientId" should {
    "grant access when the agency and client are registered for MTD and have a relationship" in {
      given().mtdAgency(agentCode, arn)
        .isAnMtdAgency()
        .andHasARelationshipWith(clientId)

      val status = authResponseFor(agentCode, clientId).status

      status shouldBe 200
    }

    "not grant access" when {
      "the agency is not registered for MTD" in {
        given().mtdAgency(agentCode, arn)
          .isNotAnMtdAgency()

        val status = authResponseFor(agentCode, clientId).status

        status shouldBe 401
      }

      "there is no relationship between the agency and client" in {
        given().mtdAgency(agentCode, arn)
          .isAnMtdAgency()
          .andHasNoRelationshipWith(clientId)

        val status = authResponseFor(agentCode, clientId).status

        status shouldBe 401
      }
    }
    "send an AccessControlDecision audit event" in {
      given()
        .mtdAgency(agentCode, arn)
        .isAnMtdAgency()
        .andHasARelationshipWith(clientId)

      authResponseFor(agentCode, clientId).status shouldBe 200

      DataStreamStub.verifyAuditRequestSent(
        AgentAccessControlDecision,
        Map("path" -> s"/agent-access-control/mtd-sa-auth/agent/$agentCode/client/${clientId.value}"))
    }

    "record metrics for access control request" in {
      val metricsRegistry = app.injector.instanceOf[MicroserviceMonitoringFilter].kenshooRegistry
      given()
        .mtdAgency(agentCode, arn)
        .isAnMtdAgency()
        .andHasARelationshipWith(clientId)

      authResponseFor(agentCode, clientId).status shouldBe 200

      metricsRegistry.getTimers().get("Timer-API-Agent-MTD-SA-Access-Control-GET").getCount should be >= 1L
    }
  }

  def authResponseFor(agentCode: AgentCode, mtdSaClientId: MtdClientId): HttpResponse =
    new Resource(s"/agent-access-control/mtd-sa-auth/agent/${agentCode.value}/client/${mtdSaClientId.value}")(port).get()
}
