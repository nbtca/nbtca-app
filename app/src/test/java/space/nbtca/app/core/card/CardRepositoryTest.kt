package space.nbtca.app.core.card
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
class CardRepositoryTest {
    @Test fun readsMaintainerCreditClaimFields() {
        val doc=Jsoup.parse("""<form class="claim"><input name="name" required maxlength="20" value="A"><input name="student_id" required maxlength="10"><input name="class_name" required><input name="phone" required maxlength="11"></form>""")
        val fields=CardRepository.claimFields(doc)
        assertEquals(listOf("name","student_id","class_name","phone"),fields.map {it.name})
        assertTrue(fields.all {it.required})
        assertEquals(11,fields.last().maxLength)
    }
    @Test fun validationErrorIsNotASuccessfulClaim() {
        val doc=Jsoup.parse("""<div class="pass-card full" data-state="preview"></div><form class="claim"><p class="notice error">学号不正确</p></form>""")
        assertEquals("学号不正确",CardRepository.claimError(doc))
        assertNotNull(CardRepository.claimError(Jsoup.parse("""<div class="pass-card full" data-state="preview"></div>""")))
        assertNull(CardRepository.claimError(Jsoup.parse("""<div class="pass-card full" data-state="valid"></div>""")))
    }
    @Test(expected=IllegalStateException::class) fun rejectsUnknownRequiredFormField() {
        CardRepository.claimFields(Jsoup.parse("""<form class="claim"><input name="new_requirement" required></form>"""))
    }
    @Test(expected=IllegalStateException::class) fun rejectsChangedProfileMarkup() {
        CardRepository.validatePage("/me",Jsoup.parse("<main>新版页面</main>"))
    }
}
