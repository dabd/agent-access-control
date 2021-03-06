package uk.gov.hmrc.agentaccesscontrol

import play.utils.UriEncoding.encodePathSegment
import uk.gov.hmrc.agentaccesscontrol.audit.AgentAccessControlEvent.AgentAccessControlDecision
import uk.gov.hmrc.agentaccesscontrol.stubs.DataStreamStub
import uk.gov.hmrc.agentaccesscontrol.support.{WireMockWithOneServerPerSuiteISpec, Resource}
import uk.gov.hmrc.domain.{AgentCode, EmpRef}
import uk.gov.hmrc.play.http.HttpResponse

class PayeAuthorisationISpec extends WireMockWithOneServerPerSuiteISpec {
  val agentCode = AgentCode("A11112222A")
  val empRef = EmpRef("123", "123456")

  "/agent-access-control/epaye-auth/agent/:agentCode/client/:empRef" should {
    "return 200 when access is granted" in {
      given().agentAdmin(agentCode).isLoggedIn()
        .andHasNoIrSaAgentEnrolment()
        .andIsAllocatedAndAssignedToClient(empRef)
        .andIsRelatedToPayeClientInDes(empRef)
        .andIsAuthorisedBy648()

      val status = authResponseFor(agentCode, empRef).status

      status shouldBe 200
    }

    "return 401 when access is not granted" in {
      given().agentAdmin(agentCode).isLoggedIn()
        .andHasNoIrSaAgentEnrolment()
        .andIsNotAllocatedToClient(empRef)
        .andIsRelatedToPayeClientInDes(empRef)
        .andIsAuthorisedBy648()

      val status = authResponseFor(agentCode, empRef).status

      status shouldBe 401
    }

    "return 502 if a downstream service fails" in{
      given().agentAdmin(agentCode).isLoggedIn()
        .andHasNoIrSaAgentEnrolment()
        .andIsAllocatedAndAssignedToClient(empRef)
        .andDesIsDown()

      val status = authResponseFor(agentCode, empRef).status

      status shouldBe 502
    }

    "record metrics for inbound http call" in {
      val metricsRegistry = app.injector.instanceOf[MicroserviceMonitoringFilter].kenshooRegistry
      given()
        .agentAdmin(agentCode).isLoggedIn()
        .andHasNoIrSaAgentEnrolment()
        .andIsAllocatedAndAssignedToClient(empRef)
        .andIsRelatedToPayeClientInDes(empRef)
        .andIsAuthorisedBy648()

      authResponseFor(agentCode, empRef).status shouldBe 200
      metricsRegistry.getTimers().get("Timer-API-Agent-PAYE-Access-Control-GET").getCount should be >= 1L
    }

    "send an AccessControlDecision audit event" in {
      given()
        .agentAdmin(agentCode).isLoggedIn()
        .andHasNoIrSaAgentEnrolment()
        .andIsAllocatedAndAssignedToClient(empRef)
        .andIsRelatedToPayeClientInDes(empRef)
        .andIsAuthorisedBy648()

      authResponseFor(agentCode, empRef).status shouldBe 200

      DataStreamStub.verifyAuditRequestSent(
        AgentAccessControlDecision,
        Map("path" -> s"/agent-access-control/epaye-auth/agent/$agentCode/client/${encodePathSegment(empRef.value, "UTF-8")}"))
    }
  }

  def authResponseFor(agentCode: AgentCode, empRef: EmpRef): HttpResponse =
    new Resource(s"/agent-access-control/epaye-auth/agent/${agentCode.value}/client/${encodePathSegment(empRef.value, "UTF-8")}")(port).get()
}
