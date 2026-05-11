package infrastructure.support

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RolePolicySeasonsSpec extends AnyWordSpec with Matchers {

  "RolePolicy.can" should {
    "permitir gestionar temporadas a super_admin y editor_jefe" in {
      RolePolicy.can("super_admin", Capabilities.Cap.SeasonsManage) shouldBe true
      RolePolicy.can("editor_jefe", Capabilities.Cap.SeasonsManage) shouldBe true
    }

    "denegar gestionar temporadas a roles sin ese permiso" in {
      RolePolicy.can("revisor", Capabilities.Cap.SeasonsManage) shouldBe false
      RolePolicy.can("analista", Capabilities.Cap.SeasonsManage) shouldBe false
    }
  }
}
