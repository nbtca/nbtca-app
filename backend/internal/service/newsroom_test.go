package service

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"space.nbtca/backend/internal/config"
)

func testConfig() *config.Config {
	return &config.Config{HTTPTimeout: 10 * time.Second}
}

func TestNewsroomFeedAndArticle(t *testing.T) {
	var rssHits int32
	var articleHits int32
	mux := http.NewServeMux()
	var server *httptest.Server

	mux.HandleFunc("/rss.xml", func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&rssHits, 1)
		w.Header().Set("Content-Type", "application/rss+xml; charset=utf-8")
		fmt.Fprintf(w, `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"><channel><title>NBTCA</title>
<item>
  <title>Go 结构体精要 2：禁用比较</title>
  <link>%s/posts/blogs/Tech/GO/go-struct-craft-2-disable-compare/</link>
  <description><![CDATA[<p>深入探讨 <strong>Go</strong> 比较机制</p>]]></description>
  <category>技术</category><category>开发</category>
  <author>zzh0u</author>
  <pubDate>Fri, 03 Apr 2026 00:00:00 GMT</pubDate>
</item>
<item>
  <title>影留宁理</title>
  <link>%s/posts/PhoNBT2024</link>
  <description>光影定格青春</description>
  <category>活动</category><category>摄影展</category>
  <author>NBTCA 媒体部</author>
  <pubDate>Wed, 18 Jun 2024 00:00:00 GMT</pubDate>
</item>
<item>
  <title>Install the Archlinux</title>
  <link>%s/posts/blogs/Tech/Linux/getArch_speedrun/</link>
  <description>Arch 极速安装</description>
  <category>技术</category><category>指南</category>
  <author>Linux Group</author>
  <pubDate>Fri, 28 Mar 2025 00:00:00 GMT</pubDate>
</item>
</channel></rss>`, server.URL, server.URL, server.URL)
	})

	mux.HandleFunc("/posts/blogs/Tech/GO/go-struct-craft-2-disable-compare/", func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&articleHits, 1)
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprint(w, `<!doctype html><html><head>
<meta property="og:image" content="/images/cover.jpg">
</head><body>
<nav>导航文字</nav>
<article>
<h1>Go 结构体精要 2</h1>
<h2>为什么需要禁用比较</h2>
<p>第一段 <strong>粗体</strong> 和 <em>斜体</em>，<a href="/about">链接</a>。</p>
<ul><li>项目一</li><li>项目二<ul><li>嵌套</li></ul></li></ul>
<pre><code class="language-go">fmt.Println("hi")</code></pre>
<blockquote>引用</blockquote>
<img src="/img/a.png" alt="图">
<hr>
<script>bad()</script>
</article>
<footer>页脚文字</footer>
</body></html>`)
	})

	server = httptest.NewServer(mux)
	defer server.Close()

	cfg := testConfig()
	cfg.SpaceSiteEndpoint = server.URL
	svc := NewNewsroomService(cfg, &http.Client{Timeout: 10 * time.Second})
	ctx := context.Background()

	feed, err := svc.GetFeed(ctx)
	if err != nil {
		t.Fatalf("GetFeed returned error: %v", err)
	}
	if len(feed.Featured) != 2 {
		t.Fatalf("featured count = %d, want 2", len(feed.Featured))
	}
	if len(feed.Recent) != 1 {
		t.Fatalf("recent count = %d, want 1", len(feed.Recent))
	}
	if feed.Featured[0].ID != "blogs/Tech/GO/go-struct-craft-2-disable-compare" {
		t.Errorf("id = %q", feed.Featured[0].ID)
	}
	if feed.Featured[0].Published != "2026 年 4 月 3 日" {
		t.Errorf("published = %q", feed.Featured[0].Published)
	}
	if feed.Featured[0].Summary != "深入探讨 Go 比较机制" {
		t.Errorf("summary = %q", feed.Featured[0].Summary)
	}
	if feed.Featured[0].ArticleURL != server.URL+"/posts/blogs/Tech/GO/go-struct-craft-2-disable-compare/" {
		t.Errorf("article url = %q", feed.Featured[0].ArticleURL)
	}
	if feed.Featured[0].CoverURL != server.URL+"/images/cover.jpg" {
		t.Errorf("feed cover = %q, want og:image", feed.Featured[0].CoverURL)
	}
	wantCategories := []string{"技术", "活动"}
	if strings.Join(feed.Categories, ",") != strings.Join(wantCategories, ",") {
		t.Errorf("categories = %v, want %v", feed.Categories, wantCategories)
	}

	if _, err := svc.GetFeed(ctx); err != nil {
		t.Fatalf("second GetFeed: %v", err)
	}
	if got := atomic.LoadInt32(&rssHits); got != 1 {
		t.Errorf("RSS hits = %d, want 1 (cache)", got)
	}

	article, err := svc.GetArticle(ctx, "blogs/Tech/GO/go-struct-craft-2-disable-compare")
	if err != nil {
		t.Fatalf("GetArticle: %v", err)
	}
	if article.CoverURL != server.URL+"/images/cover.jpg" {
		t.Errorf("cover = %q", article.CoverURL)
	}
	md := article.ContentMarkdown
	for _, want := range []string{"# Go 结构体精要 2", "## 为什么需要禁用比较", "**粗体**", "*斜体*", "[链接](" + server.URL + "/about)", "- 项目一", "  - 嵌套", "```go", "> 引用", "![图](" + server.URL + "/img/a.png)", "---"} {
		if !strings.Contains(md, want) {
			t.Errorf("markdown missing %q: %s", want, md)
		}
	}
	for _, unwanted := range []string{"导航文字", "页脚文字", "bad()", "<script"} {
		if strings.Contains(md, unwanted) {
			t.Errorf("markdown contains %q: %s", unwanted, md)
		}
	}
	if _, err := svc.GetArticle(ctx, "blogs/Tech/GO/go-struct-craft-2-disable-compare"); err != nil {
		t.Fatalf("second GetArticle: %v", err)
	}
	if got := atomic.LoadInt32(&articleHits); got != 1 {
		t.Errorf("article hits = %d, want 1 (cache)", got)
	}
}

func TestNewsroomFeedUnavailableReturnsEmpty(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "boom", http.StatusInternalServerError)
	}))
	defer server.Close()

	cfg := testConfig()
	cfg.SpaceSiteEndpoint = server.URL
	svc := NewNewsroomService(cfg, nil)
	feed, err := svc.GetFeed(context.Background())
	if err == nil {
		t.Fatal("expected error from unavailable RSS")
	}
	if feed.Featured == nil || feed.Recent == nil || feed.Categories == nil {
		t.Fatal("empty feed slices must be non-nil")
	}
	if len(feed.Featured) != 0 || len(feed.Recent) != 0 || len(feed.Categories) != 0 {
		t.Fatalf("expected empty feed, got %+v", feed)
	}
}

func TestNewsroomArticleFallsBack(t *testing.T) {
	mux := http.NewServeMux()
	var server *httptest.Server
	mux.HandleFunc("/rss.xml", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintf(w, `<rss version="2.0"><channel><item><title>Fallback</title><link>%s/posts/fallback</link><description>摘要内容</description><category>技术</category><pubDate>Fri, 03 Apr 2026 00:00:00 GMT</pubDate></item></channel></rss>`, server.URL)
	})
	mux.HandleFunc("/posts/fallback", func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "no body", http.StatusBadGateway)
	})
	server = httptest.NewServer(mux)
	defer server.Close()

	cfg := testConfig()
	cfg.SpaceSiteEndpoint = server.URL
	svc := NewNewsroomService(cfg, nil)
	article, err := svc.GetArticle(context.Background(), "fallback")
	if err != nil {
		t.Fatalf("GetArticle: %v", err)
	}
	if !strings.Contains(article.ContentMarkdown, "原文链接") || !strings.Contains(article.ContentMarkdown, "摘要内容") {
		t.Fatalf("fallback markdown = %q", article.ContentMarkdown)
	}
}
