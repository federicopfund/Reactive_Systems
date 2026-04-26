package controllers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import models.EditorialStageCode

class SeasonAutoAssignmentSpec extends AnyWordSpec with Matchers {

  "SeasonAutoAssignment.shouldAssign" should {
    "return true for scheduled and published when publication has no season" in {
      SeasonAutoAssignment.shouldAssign(EditorialStageCode.Scheduled, None) shouldBe true
      SeasonAutoAssignment.shouldAssign(EditorialStageCode.Published, None) shouldBe true
    }

    "return false for non-public stages" in {
      SeasonAutoAssignment.shouldAssign(EditorialStageCode.PendingApproval, None) shouldBe false
      SeasonAutoAssignment.shouldAssign(EditorialStageCode.InDraft, None) shouldBe false
    }

    "return false when publication already has a season assigned" in {
      SeasonAutoAssignment.shouldAssign(EditorialStageCode.Scheduled, Some(10L)) shouldBe false
      SeasonAutoAssignment.shouldAssign(EditorialStageCode.Published, Some(10L)) shouldBe false
    }
  }
}
