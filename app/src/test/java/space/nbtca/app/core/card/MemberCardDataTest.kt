package space.nbtca.app.core.card

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MemberCardDataTest {
    @Test fun keepsBackgroundWithoutPortraitAndReadsCardWord() {
        val html = """<div class="card full" style="--hue: 248; --fit: 20px">
            <div class="sky"><img src="/media/members/test/background.png"></div>
            <span class="word">YUNCI</span><div class="subject"><span class="ring"></span></div>
            <header class="topline">NBTCA <span>Since 2025</span></header>
            <p class="name">Yuna Celisse</p><p class="sub">开发部 · 部长</p>
            <div class="back"><dl><div><dt>代号</dt><dd>yunacelisse</dd></div><div><dt>入会</dt><dd>2025</dd></div>
            <div><dt>GitHub</dt><dd>@Yuna-Celisse</dd></div></dl><p class="motto">Vibe</p></div></div>"""
        val data = MemberCardData.parse(Jsoup.parse(html).selectFirst(".card")!!)
        assertEquals("/media/members/test/background.png",data.background)
        assertNull(data.portrait)
        assertEquals("YUNCI",data.word)
        assertEquals("yunacelisse",data.alias)
        assertEquals(248f,data.hue)
        assertEquals("开发部",data.section)
        assertEquals("部长",data.role)
        assertEquals("Yuna-Celisse",data.github)
    }
    @Test fun thumbnailKeepsBothImageLayersAndMembershipYear() {
        val html="""<a class="card thumb" href="/members/member"><div class="sky"><img src="/media/members/member/bg.thumb.png"></div>
            <div class="subject"><img src="/media/members/member/portrait.thumb.png"></div><span class="word">HELLO</span>
            <div class="topline">NBTCA <span>Since 2024</span></div><p class="name">Name</p><p class="sub">技术部 · 社员</p></a>"""
        val data=MemberCardData.parse(Jsoup.parse(html).selectFirst(".card")!!)
        assertEquals("2024",data.joined)
        assertEquals("/members/member",data.path)
        assertNotEquals(data.background,data.portrait)
        assertEquals("/media/members/member/portrait.thumb.png",data.portrait)
    }
}
